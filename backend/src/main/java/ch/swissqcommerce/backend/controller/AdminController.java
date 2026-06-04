package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.model.ChaosFaultLog;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Admin operations.
 * Handles chaos fault injection/resolution, onboarding gate approvals,
 * HITL queue management, and system health monitoring.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Data
    public static class InjectFaultRequest {
        @NotBlank(message = "Fault type is required")
        private String faultType;

        private String details;
    }

    @Data
    public static class OnboardingApprovalRequest {
        @NotBlank(message = "Gate is required")
        private String gate;
    }

    @Data
    public static class HitlDecisionRequest {
        @NotBlank(message = "Decision is required (approve/reject)")
        private String decision;

        private String reason;
    }

    /**
     * POST /api/admin/chaos/faults - Inject a chaos fault.
     */
    @PostMapping("/chaos/faults")
    public ResponseEntity<ChaosFaultLog> injectFault(@Valid @RequestBody InjectFaultRequest request) {
        ChaosFaultLog fault = adminService.injectFault(request.getFaultType(), request.getDetails());
        return ResponseEntity.status(201).body(fault);
    }

    /**
     * POST /api/admin/chaos/{id}/resolve - Resolve a chaos fault.
     */
    @PostMapping("/chaos/{id}/resolve")
    public ResponseEntity<ChaosFaultLog> resolveFault(@PathVariable Integer id) {
        ChaosFaultLog fault = adminService.resolveFault(id);
        return ResponseEntity.ok(fault);
    }

    /**
     * GET /api/admin/chaos/active - Get all active faults.
     */
    @GetMapping("/chaos/active")
    public ResponseEntity<List<ChaosFaultLog>> getActiveFaults() {
        return ResponseEntity.ok(adminService.getActiveFaults());
    }

    /**
     * POST /api/admin/onboard/queue/{appId}/approve - Approve onboarding gate.
     */
    @PostMapping("/onboard/queue/{appId}/approve")
    public ResponseEntity<Map<String, Object>> approveOnboarding(
            @PathVariable String appId,
            @Valid @RequestBody OnboardingApprovalRequest request) {
        Map<String, Object> result = adminService.approveOnboarding(appId, request.getGate());
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/admin/hitl/queue - Get pending HITL tickets.
     */
    @GetMapping("/hitl/queue")
    public ResponseEntity<List<HitlQueue>> getPendingHitlTickets() {
        return ResponseEntity.ok(adminService.getPendingHitlTickets());
    }

    /**
     * POST /api/admin/hitl/queue/{ticketId}/resolve - Resolve a HITL ticket.
     */
    @PostMapping("/hitl/queue/{ticketId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveHitlTicket(
            @PathVariable String ticketId,
            @Valid @RequestBody HitlDecisionRequest request) {
        Map<String, Object> result = adminService.resolveHitlTicket(
                ticketId, request.getDecision(), request.getReason());
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/admin/health - System health dashboard.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        return ResponseEntity.ok(adminService.getSystemHealth());
    }
}
