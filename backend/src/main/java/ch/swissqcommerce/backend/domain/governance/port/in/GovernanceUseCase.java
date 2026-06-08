package ch.swissqcommerce.backend.domain.governance.port.in;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import java.math.BigDecimal;
import java.util.List;

public interface GovernanceUseCase {
    void auditNegotiation(Integer restockOrderId, String wholesalerId, BigDecimal amount);
    void approveOverride(Integer approvalId, String operator, String reason);
    void rejectOverride(Integer approvalId, String operator, String reason);
    String signDeliverySummary(String orderId, String podHash);
    List<ProcurementApproval> getPendingApprovals();
}
