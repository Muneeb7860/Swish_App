package ch.swissqcommerce.backend.domain.governance.adapter.in.web;

import ch.swissqcommerce.backend.domain.governance.core.model.HitlItem;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Supervisor HITL console API (Phase 8). Serves the unified queue (B2B
 * procurement overrides + agent escalations) and routes approve/reject by the
 * item's composite id. ADMIN-only and idempotent (resolving a non-pending item
 * yields 409 via TicketAlreadyResolvedException).
 */
@RestController
@RequestMapping("/api/governance/hitl")
@CrossOrigin(origins = "*")
public class HitlQueueController {

    private final GovernanceUseCase governanceUseCase;

    public HitlQueueController(GovernanceUseCase governanceUseCase) {
        this.governanceUseCase = governanceUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<HitlItem>> getPendingHitlItems() {
        return ResponseEntity.ok(governanceUseCase.getPendingHitlItems());
    }

    public static class OverrideRequest {
        private String operator;
        private String reason;

        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String id,
                                                       @RequestBody OverrideRequest request) {
        governanceUseCase.resolveHitlItem(id, true, request.getOperator(), request.getReason());
        return ResponseEntity.ok(Map.of(
                "id", id,
                "status", "APPROVED",
                "message", "HITL item approved and released."
        ));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable String id,
                                                      @RequestBody OverrideRequest request) {
        governanceUseCase.resolveHitlItem(id, false, request.getOperator(), request.getReason());
        return ResponseEntity.ok(Map.of(
                "id", id,
                "status", "REJECTED",
                "message", "HITL item rejected."
        ));
    }

    public static class AdjustRequest {
        private double newPrice;
        private String operator;
        private String reason;

        public double getNewPrice() { return newPrice; }
        public void setNewPrice(double newPrice) { this.newPrice = newPrice; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    @PostMapping("/{id}/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adjust(@PathVariable String id,
                                                      @RequestBody AdjustRequest request) {
        governanceUseCase.adjustHitlItem(id, request.getNewPrice(), request.getOperator(), request.getReason());
        return ResponseEntity.ok(Map.of(
                "id", id,
                "status", "ADJUSTED",
                "message", "HITL item adjusted and approved."
        ));
    }
}
