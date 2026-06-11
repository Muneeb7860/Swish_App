package ch.swissqcommerce.backend.domain.sensor.adapter.in.web;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;
import ch.swissqcommerce.backend.domain.sensor.port.in.SensorUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Sensor (device) provisioning API (BRD FR-01 sensor provisioning). Operator-
 * gated. The device-key hash is never exposed; the plaintext key is returned
 * exactly once, at provisioning.
 */
@RestController
@RequestMapping("/api/v1/sensors")
@RequiredArgsConstructor
@Tag(name = "Sensor", description = "IoT device provisioning for retailer hubs")
public class SensorController {

    private final SensorUseCase sensors;

    public record ProvisionRequest(String retailerId, String storeId, SensorType type) {}

    public record SensorView(String sensorId, String retailerId, String storeId,
                             SensorType sensorType, String status) {
        static SensorView of(Sensor s) {
            return new SensorView(s.getSensorId(), s.getRetailerId(), s.getStoreId(),
                    s.getSensorType(), s.getStatus());
        }
    }

    @Operation(summary = "Provision a sensor for a retailer hub")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> provision(@RequestBody ProvisionRequest req) {
        try {
            SensorUseCase.ProvisionResult result = sensors.provision(req.retailerId(), req.storeId(), req.type());
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("sensor", SensorView.of(result.sensor()));
            body.put("deviceKey", result.deviceKey());
            body.put("message", "Store this device key securely; it will not be shown again.");
            return ResponseEntity.status(201).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Activate a provisioned sensor")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{sensorId}/activate")
    public ResponseEntity<?> activate(@PathVariable String sensorId) {
        return transition(() -> SensorView.of(sensors.activate(sensorId)));
    }

    @Operation(summary = "Decommission a sensor")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{sensorId}/decommission")
    public ResponseEntity<?> decommission(@PathVariable String sensorId) {
        return transition(() -> SensorView.of(sensors.decommission(sensorId)));
    }

    @Operation(summary = "List sensors for a retailer")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<SensorView>> listByRetailer(@RequestParam String retailerId) {
        return ResponseEntity.ok(sensors.listByRetailer(retailerId).stream().map(SensorView::of).toList());
    }

    private ResponseEntity<?> transition(java.util.function.Supplier<SensorView> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
