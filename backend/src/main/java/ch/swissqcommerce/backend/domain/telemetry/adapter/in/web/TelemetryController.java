package ch.swissqcommerce.backend.domain.telemetry.adapter.in.web;
import java.util.List;
import java.util.ArrayList;


import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import ch.swissqcommerce.backend.domain.telemetry.port.in.TelemetryUseCase;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.service.InMemoryGeoStore;
import ch.swissqcommerce.backend.repository.OrderRepository;
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

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    @Autowired
    private TelemetryUseCase telemetryService;

    @Autowired
    private InMemoryGeoStore geoStore;

    @Autowired
    private TelemetryPort telemetryPort;

    @Autowired
    private OrderRepository orderRepository;

    private final java.util.concurrent.ConcurrentLinkedQueue<TelemetryTickRequest> tickBuffer = new java.util.concurrent.ConcurrentLinkedQueue<>();

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 10000)
    public void flushTickBuffer() {
        if (tickBuffer.isEmpty()) return;
        java.util.List<TelemetryTickRequest> ticksToFlush = new java.util.ArrayList<>();
        TelemetryTickRequest tick;
        while ((tick = tickBuffer.poll()) != null) {
            ticksToFlush.add(tick);
        }
        if (ticksToFlush.isEmpty()) return;
        for (TelemetryTickRequest request : ticksToFlush) {
            try {
                OrderTelemetryLogEntity log = OrderTelemetryLogEntity.builder()
                        .orderId(request.getOrderId())
                        .deviceTimestamp(OffsetDateTime.now())
                        .latitude(request.getLatitude())
                        .longitude(request.getLongitude())
                        .temperature(request.getTemperature())
                        .dryIceInjected(request.isDryIceInjected())
                        .alertTriggered(false)
                        .build();
                telemetryPort.save(log);
            } catch (Exception e) {
                tickBuffer.add(request); // Re-queue on failure
            }
        }
    }

    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public static class TelemetryTickRequest {
        @jakarta.validation.constraints.NotNull(message = "Order ID is required")
        private Integer orderId;

        @jakarta.validation.constraints.NotNull(message = "Latitude is required")
        @jakarta.validation.constraints.DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
        @jakarta.validation.constraints.DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
        private BigDecimal latitude;

        @jakarta.validation.constraints.NotNull(message = "Longitude is required")
        @jakarta.validation.constraints.DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
        @jakarta.validation.constraints.DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
        private BigDecimal longitude;

        @jakarta.validation.constraints.NotNull(message = "Temperature is required")
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

    @PostMapping("/tick")
    public ResponseEntity<Map<String, Object>> ingestTick(@jakarta.validation.Valid @RequestBody TelemetryTickRequest request) {
        geoStore.updateLocation(request.getOrderId(), request.getLatitude(), request.getLongitude(), request.getTemperature());

        boolean thresholdBreached = request.getTemperature().compareTo(new BigDecimal("8.0")) > 0;
        boolean dryIceInjected = request.isDryIceInjected();

        OrderTelemetryLogEntity savedDbLog = null;
        if (thresholdBreached || dryIceInjected) {
            savedDbLog = telemetryService.recordTelemetry(
                    request.getOrderId(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getTemperature(),
                    request.isDryIceInjected()
            );
        } else {
            tickBuffer.add(request);
        }

        boolean thermalBreachActive = telemetryService.isThermalBreachActive(request.getOrderId(), request.getTemperature());

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", request.getOrderId());
        payload.put("latitude", request.getLatitude());
        payload.put("longitude", request.getLongitude());
        payload.put("temperature", request.getTemperature());
        payload.put("dryIceInjected", request.isDryIceInjected());
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("alertTriggered", thresholdBreached);
        payload.put("thermalBreachActive", thermalBreachActive);
        payload.put("persisted", savedDbLog != null || !thresholdBreached);
        payload.put("queued", savedDbLog == null && !thresholdBreached);

        pushToSubscribers(request.getOrderId(), payload);

        return ResponseEntity.ok(payload);
    }

    @GetMapping(value = "/stream/{orderId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetry(@PathVariable Integer orderId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> removeEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeEmitter(orderId, emitter));
        emitter.onError((ex) -> removeEmitter(orderId, emitter));

        InMemoryGeoStore.RiderLocation currentLoc = geoStore.getLatestLocation(orderId);
        if (currentLoc != null) {
            try {
                boolean thermalBreachActive = telemetryService.isThermalBreachActive(orderId, currentLoc.getTemperature());
                Map<String, Object> payload = new HashMap<>();
                payload.put("orderId", orderId);
                payload.put("latitude", currentLoc.getLatitude());
                payload.put("longitude", currentLoc.getLongitude());
                payload.put("temperature", currentLoc.getTemperature());
                payload.put("timestamp", currentLoc.getTimestamp().toString());
                payload.put("thermalBreachActive", thermalBreachActive);
                payload.put("initial", true);
                emitter.send(SseEmitter.event().name("telemetry-update").data(payload));
            } catch (IOException ignored) {}
        }

        return emitter;
    }

    @PostMapping("/{orderId}/dry-ice")
    public ResponseEntity<Map<String, Object>> injectDryIce(@PathVariable Integer orderId) {
        telemetryService.injectDryIce(orderId);
        
        InMemoryGeoStore.RiderLocation currentLoc = geoStore.getLatestLocation(orderId);
        BigDecimal lat = currentLoc != null ? currentLoc.getLatitude() : new BigDecimal("47.3769");
        BigDecimal lng = currentLoc != null ? currentLoc.getLongitude() : new BigDecimal("8.5417");
        
        geoStore.updateLocation(orderId, lat, lng, new BigDecimal("4.0"));
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("latitude", lat);
        payload.put("longitude", lng);
        payload.put("temperature", new BigDecimal("4.0"));
        payload.put("dryIceInjected", true);
        payload.put("thermalBreachActive", false);
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
