package ch.swissqcommerce.backend.domain.agent.core.service;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface B2BProcurementActivities {
    B2BProcurementAgent.NegotiationAnalysis callLlmNegotiation(
            String itemId, String itemName, double basePrice, String wholesalerName);

    boolean checkGuardrail(double proposedPrice, double basePrice, int quantity);

    int createPendingOrder(
            String itemId, String wholesalerName, double proposedPrice, int quantity);

    int createFulfilledOrder(
            String itemId, String wholesalerName, double proposedPrice, int quantity);

    void auditNegotiation(
            int restockOrderId, String wholesalerName, double proposedPrice, int quantity);

    void updateOrderStatus(int restockOrderId, String status);

    void updateOrderPrice(int restockOrderId, double newPrice);

    void updateApprovalStatus(int restockOrderId, String status, String operator, String reason);
}
