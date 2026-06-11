package ch.swissqcommerce.backend.domain.agent.core.service;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface B2BProcurementWorkflow {
    @WorkflowMethod
    B2BProcurementAgent.NegotiationAnalysis negotiateRestock(
            String itemId, String itemName, double basePrice, String wholesalerName);
}
