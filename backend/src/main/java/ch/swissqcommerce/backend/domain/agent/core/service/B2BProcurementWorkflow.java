package ch.swissqcommerce.backend.domain.agent.core.service;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface B2BProcurementWorkflow {
    @WorkflowMethod
    B2BProcurementAgent.NegotiationAnalysis negotiateRestock(
            String itemId, String itemName, double basePrice, String wholesalerName, int quantity);

    @SignalMethod
    void approve(String operator, String reason);

    @SignalMethod
    void reject(String operator, String reason);

    @SignalMethod
    void adjustBid(double newPrice, String operator, String reason);
}
