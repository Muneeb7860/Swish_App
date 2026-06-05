package ch.swissqcommerce.backend.domain.governance.port.in;

import java.math.BigDecimal;

public interface GovernanceUseCase {
    void auditNegotiation(Integer restockOrderId, String wholesalerId, BigDecimal amount);
    void approveOverride(Integer approvalId, String operator, String reason);
    void rejectOverride(Integer approvalId, String operator, String reason);
    String signDeliverySummary(String orderId);
}
