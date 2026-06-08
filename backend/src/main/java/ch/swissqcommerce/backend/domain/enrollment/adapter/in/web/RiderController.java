package ch.swissqcommerce.backend.domain.enrollment.adapter.in.web;
import java.math.BigDecimal;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/riders")
public class RiderController {
    
    public static class TelemetryPingRequest {
        public java.math.BigDecimal latitude;
        public java.math.BigDecimal longitude;
        public java.math.BigDecimal temperature;
    }

    @PostMapping("/{riderId}/telemetry")
    public ResponseEntity<Void> recordTelemetry(
        @PathVariable String riderId,
        @RequestBody TelemetryPingRequest request
    ) {
        return ResponseEntity.ok().build();
    }
}
