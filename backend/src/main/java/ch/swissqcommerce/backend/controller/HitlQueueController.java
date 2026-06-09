package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @deprecated Superseded by {@link ch.swissqcommerce.backend.domain.governance.adapter.in.web.HitlQueueController}.
 * Deactivated (no @RestController) to avoid duplicate URL mapping conflicts.
 */
// @RestController — intentionally disabled; see Javadoc above
@RequestMapping("/api/governance/hitl")
@CrossOrigin(origins = "*")
public class HitlQueueController {

    private final GovernanceUseCase governanceUseCase;
    private final ProcurementApprovalRepository approvalsRepository;

    public HitlQueueController(GovernanceUseCase governanceUseCase,
                               ProcurementApprovalRepository approvalsRepository) {
        this.governanceUseCase = governanceUseCase;
        this.approvalsRepository = approvalsRepository;
    }

    @GetMapping
    public ResponseEntity<List<ProcurementApproval>> getPendingApprovals() {
        List<ProcurementApproval> approvals = approvalsRepository.findAll().stream()
                .filter(e -> "PENDING".equalsIgnoreCase(e.getStatus()))
                .map(e -> ProcurementApproval.builder()
                        .id(e.getId())
                        .restockOrderId(e.getRestockOrderId())
                        .wholesalerId(e.getWholesalerId())
                        .amount(e.getAmount())
                        .status(e.getStatus())
                        .overrideBy(e.getOverrideBy())
                        .overrideReason(e.getOverrideReason())
                        .build())
                .toList();
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
