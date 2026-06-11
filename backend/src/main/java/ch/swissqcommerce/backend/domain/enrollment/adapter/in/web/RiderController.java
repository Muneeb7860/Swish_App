package ch.swissqcommerce.backend.domain.enrollment.adapter.in.web;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.enrollment.port.in.RiderUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

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
        @JsonProperty("full_name")
        @JsonAlias({"name", "fullName"})
        @NotBlank(message = "Name is required")
        private String name;

        @JsonProperty("vehicle_type")
        @JsonAlias({"vehicleType", "vehicle_type"})
        @NotBlank(message = "Vehicle type is required")
        private String vehicleType;

        @JsonProperty("driver_license_base64")
        @JsonAlias({"details", "driverLicenseBase64"})
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
     * Restricted to ROLE_ADMIN — riders must not be able to self-approve their own applications.
     */
    @PreAuthorize("hasRole('ADMIN')")
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
     * The authenticated rider must be the one assigned to the order — a rider cannot
     * submit telemetry for another rider's delivery.
     */
    @PostMapping("/orders/{id}/telemetry")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<?> recordTelemetry(
            @PathVariable Integer id,
            @Valid @RequestBody TelemetryPingRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized."));
        }
        String callerRiderId = auth.getName();
        try {
            OrderTelemetryLog log = riderUseCase.recordPing(
                    id, request.getLatitude(), request.getLongitude(), request.getTemperature(), callerRiderId);
            return ResponseEntity.status(201).body(log);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", e.getMessage()));
        }
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
    public ResponseEntity<?> completeCourse(
            @PathVariable String courseId,
            @RequestParam(required = false) String riderId) {
        if (riderId == null || riderId.isBlank()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                riderId = auth.getName();
            }
        }
        return ResponseEntity.ok(riderUseCase.completeAcademyCourse(riderId, courseId));
    }
}
