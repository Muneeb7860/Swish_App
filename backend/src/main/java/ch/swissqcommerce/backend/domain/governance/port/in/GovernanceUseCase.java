package ch.swissqcommerce.backend.domain.governance.port.in;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.core.model.HitlItem;
import java.math.BigDecimal;
import java.util.List;

public interface GovernanceUseCase {
    void auditNegotiation(Integer restockOrderId, String wholesalerId, BigDecimal amount);
    void approveOverride(Integer approvalId, String operator, String reason);
    void rejectOverride(Integer approvalId, String operator, String reason);
    String signDeliverySummary(String orderId, String podHash);
    List<ProcurementApproval> getPendingApprovals();

    /**
     * Unified supervisor queue (Phase 8): pending B2B procurement overrides +
     * pending agent escalations, as one list of {@link HitlItem}.
     */
    List<HitlItem> getPendingHitlItems();

    /**
     * Approve or reject a unified HITL item by its composite id ({@code "PA-<id>"}
     * for procurement, {@code "AQ-<ticketId>"} for agent escalations). Idempotent:
     * resolving an already-resolved item throws {@code TicketAlreadyResolvedException}.
     */
    void resolveHitlItem(String compositeId, boolean approve, String operator, String reason);
    void adjustHitlItem(String compositeId, double newPrice, String operator, String reason);
    void adjustOverride(Integer approvalId, double newPrice, String operator, String reason);
}
