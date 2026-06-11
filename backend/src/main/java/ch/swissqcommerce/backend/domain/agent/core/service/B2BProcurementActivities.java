package ch.swissqcommerce.backend.domain.agent.core.service;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface B2BProcurementActivities {
    @ActivityMethod
    B2BProcurementAgent.NegotiationAnalysis callLlmNegotiation(
            String itemId, String itemName, double basePrice, String wholesalerName);
}
