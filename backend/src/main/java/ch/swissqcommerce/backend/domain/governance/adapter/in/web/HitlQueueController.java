package ch.swissqcommerce.backend.domain.governance.adapter.in.web;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<List<ProcurementApproval>> getPendingApprovals() {
        List<ProcurementApproval> approvals = governanceUseCase.getPendingApprovals();
        return ResponseEntity.ok(approvals);
    }

    public static class OverrideRequest {
        private String operator;
        private String reason;

        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    @PostMapping("/{approvalId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Integer approvalId, 
                                                       @RequestBody OverrideRequest request) {
        governanceUseCase.approveOverride(approvalId, request.getOperator(), request.getReason());
        return ResponseEntity.ok(Map.of(
                "approvalId", approvalId,
                "status", "APPROVED",
                "message", "B2B restock transaction successfully overridden and released."
        ));
    }

    @PostMapping("/{approvalId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Integer approvalId, 
                                                       @RequestBody OverrideRequest request) {
        governanceUseCase.rejectOverride(approvalId, request.getOperator(), request.getReason());
        return ResponseEntity.ok(Map.of(
                "approvalId", approvalId,
                "status", "REJECTED",
                "message", "B2B restock transaction override rejected. Order canceled."
        ));
    }
}
