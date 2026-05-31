package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.service.InMemoryGeoStore;
import ch.swissqcommerce.backend.service.TelemetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enterprise Blueprint: Telemetry Controller.
 * Ingests high-frequency rider ticks and broadcasts them to clients in real-time
 * using Server-Sent Events (SSE). Optimizes DB resources by avoiding write amplification.
 */
@RestController
@RequestMapping("/api/telemetry")
@CrossOrigin(origins = "*")
public class TelemetryController {

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private InMemoryGeoStore geoStore;

    // Thread-safe map of orderId to active client SSE streams
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public static class TelemetryTickRequest {
        private Integer orderId;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private BigDecimal temperature;
        private boolean dryIceInjected;

        public Integer getOrderId() { return orderId; }
        public void setOrderId(Integer orderId) { this.orderId = orderId; }
        public BigDecimal getLatitude() { return latitude; }
        public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
        public BigDecimal getLongitude() { return longitude; }
        public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
        public BigDecimal getTemperature() { return temperature; }
        public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
        public boolean isDryIceInjected() { return dryIceInjected; }
        public void setDryIceInjected(boolean dryIceInjected) { this.dryIceInjected = dryIceInjected; }
    }

    /**
     * POST /api/telemetry/tick
     * Ingests a new GPS and thermal telemetry tick.
     * Caches in-memory for low-latency distribution, and only persists to database
     * on active alerts or cooling actions to minimize database write-amplification.
     */
    @PostMapping("/tick")
    public ResponseEntity<Map<String, Object>> ingestTick(@RequestBody TelemetryTickRequest request) {
        // 1. Cache latest location in high-performance in-memory geo-store
        geoStore.updateLocation(request.getOrderId(), request.getLatitude(), request.getLongitude(), request.getTemperature());

        boolean thresholdBreached = request.getTemperature().compareTo(new BigDecimal("8.0")) > 0;
        boolean dryIceInjected = request.isDryIceInjected();

        OrderTelemetryLog savedDbLog = null;
        // 2. Database Decoupling: Only write to the OLTP database during thermal spikes or injection actions
        if (thresholdBreached || dryIceInjected) {
            savedDbLog = telemetryService.recordTelemetry(
                    request.getOrderId(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getTemperature(),
                    request.isDryIceInjected()
            );
        }

        // 3. Construct the payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", request.getOrderId());
        payload.put("latitude", request.getLatitude());
        payload.put("longitude", request.getLongitude());
        payload.put("temperature", request.getTemperature());
        payload.put("dryIceInjected", request.isDryIceInjected());
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("alertTriggered", thresholdBreached);
        payload.put("persisted", savedDbLog != null);

        // 4. Push updates to all active SSE streaming clients
        pushToSubscribers(request.getOrderId(), payload);

        return ResponseEntity.ok(payload);
    }

    /**
     * GET /api/telemetry/stream/{orderId}
     * Establishes a real-time Server-Sent Events (SSE) stream for customer tracking.
     */
    @GetMapping(value = "/stream/{orderId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetry(@PathVariable Integer orderId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3-minute socket timeout
        
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> removeEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeEmitter(orderId, emitter));
        emitter.onError((ex) -> removeEmitter(orderId, emitter));

        // Push immediate current location if available
        InMemoryGeoStore.RiderLocation currentLoc = geoStore.getLatestLocation(orderId);
        if (currentLoc != null) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("orderId", orderId);
                payload.put("latitude", currentLoc.getLatitude());
                payload.put("longitude", currentLoc.getLongitude());
                payload.put("temperature", currentLoc.getTemperature());
                payload.put("timestamp", currentLoc.getTimestamp().toString());
                payload.put("initial", true);
                emitter.send(SseEmitter.event().name("telemetry-update").data(payload));
            } catch (IOException ignored) {}
        }

        return emitter;
    }

    /**
     * POST /api/telemetry/{orderId}/dry-ice
     * Exposes dry ice coolant injection interface.
     */
    @PostMapping("/{orderId}/dry-ice")
    public ResponseEntity<Map<String, Object>> injectDryIce(@PathVariable Integer orderId) {
        telemetryService.injectDryIce(orderId);
        
        InMemoryGeoStore.RiderLocation currentLoc = geoStore.getLatestLocation(orderId);
        BigDecimal lat = currentLoc != null ? currentLoc.getLatitude() : new BigDecimal("47.3769");
        BigDecimal lng = currentLoc != null ? currentLoc.getLongitude() : new BigDecimal("8.5417");
        
        // Reset cached temperature to 4.0 after dry ice cooling
        geoStore.updateLocation(orderId, lat, lng, new BigDecimal("4.0"));
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("latitude", lat);
        payload.put("longitude", lng);
        payload.put("temperature", new BigDecimal("4.0"));
        payload.put("dryIceInjected", true);
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("message", "Dry ice cargo cooling completed.");
        
        pushToSubscribers(orderId, payload);
        
        return ResponseEntity.ok(payload);
    }

    private void pushToSubscribers(Integer orderId, Object payload) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(orderId);
        if (list != null && !list.isEmpty()) {
            java.util.List<SseEmitter> deadEmitters = new java.util.ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("telemetry-update").data(payload));
                } catch (Exception ex) {
                    deadEmitters.add(emitter);
                }
            }
            list.removeAll(deadEmitters);
        }
    }

    private void removeEmitter(Integer orderId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(orderId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
