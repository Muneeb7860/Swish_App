package ch.swissqcommerce.backend.domain.enrollment.adapter.in.web;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.enrollment.port.in.RiderUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST controller for Rider operations.
 * Handles onboarding submissions, GPS telemetry pings,
 * coolant injection triggers, and delivery confirmations.
 */
@RestController
@RequestMapping("/api/rider")
public class RiderController {

    @Autowired
    private RiderUseCase riderUseCase;

    @Data
    public static class OnboardingRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Vehicle type is required")
        private String vehicleType;

        private String details;
    }

    @Data
    public static class TelemetryPingRequest {
        @NotNull(message = "Latitude is required")
        private BigDecimal latitude;

        @NotNull(message = "Longitude is required")
        private BigDecimal longitude;

        @NotNull(message = "Temperature is required")
        private BigDecimal temperature;
    }

    /**
     * POST /api/rider/onboard - Submit rider onboarding application.
     */
    @PostMapping("/onboard")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitOnboarding(@Valid @RequestBody OnboardingRequest request) {
        Map<String, Object> result = riderUseCase.submitOnboarding(
                request.getName(), request.getVehicleType(), request.getDetails());
        return ResponseEntity.status(201).body(result);
    }

    /**
     * POST /api/rider/onboard/{applicationId}/approve - Approve a specific gate for rider onboarding application.
     */
    @PostMapping("/onboard/{applicationId}/approve")
    @Transactional
    public ResponseEntity<Map<String, Object>> approveOnboarding(
            @PathVariable String applicationId,
            @RequestParam String gate) {
        Map<String, Object> result = riderUseCase.approveOnboarding(applicationId, gate);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/rider/orders/{id}/coolant - Inject dry ice coolant.
     */
    @PostMapping("/orders/{id}/coolant")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<Map<String, Object>> injectCoolant(@PathVariable Integer id) {
        Map<String, Object> result = riderUseCase.injectCoolant(id);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/rider/orders/{id}/telemetry - Record GPS/thermal telemetry ping.
     */
    @PostMapping("/orders/{id}/telemetry")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<OrderTelemetryLog> recordTelemetry(
            @PathVariable Integer id,
            @Valid @RequestBody TelemetryPingRequest request) {
        OrderTelemetryLog log = riderUseCase.recordPing(
                id, request.getLatitude(), request.getLongitude(), request.getTemperature());
        return ResponseEntity.status(201).body(log);
    }

    @Data
    public static class ConfirmDeliveryRequest {
        private String pin;
        private String photoUrl;
    }

    @Data
    public static class RejectDeliveryRequest {
        @NotBlank(message = "Rejection reason is required")
        private String reason;
        
        @NotBlank(message = "Rejection photo URL is required")
        private String photoUrl;
    }

    /**
     * POST /api/rider/orders/{id}/deliver - Confirm delivery with PIN or Photo.
     */
    @PostMapping("/orders/{id}/deliver")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<Map<String, Object>> confirmDelivery(
            @PathVariable Integer id,
            @Valid @RequestBody ConfirmDeliveryRequest request) {
        Map<String, Object> result = riderUseCase.confirmDelivery(id, request.getPin(), request.getPhotoUrl());
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/rider/orders/{id}/reject - Reject delivery at the door.
     */
    @PostMapping("/orders/{id}/reject")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<Map<String, Object>> rejectDelivery(
            @PathVariable Integer id,
            @Valid @RequestBody RejectDeliveryRequest request) {
        Map<String, Object> result = riderUseCase.rejectDelivery(id, request.getReason(), request.getPhotoUrl());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/academy/courses")
    public ResponseEntity<?> getCourses() {
        return ResponseEntity.ok(riderUseCase.getAcademyCourses());
    }

    @PostMapping("/academy/courses/{courseId}/complete")
    @Transactional
    public ResponseEntity<?> completeCourse(@PathVariable String courseId, @RequestParam String riderId) {
        return ResponseEntity.ok(riderUseCase.completeAcademyCourse(riderId, courseId));
    }
}
