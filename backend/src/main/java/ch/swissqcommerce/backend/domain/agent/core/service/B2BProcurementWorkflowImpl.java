package ch.swissqcommerce.backend.domain.agent.core.service;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class B2BProcurementWorkflowImpl implements B2BProcurementWorkflow {

    private final B2BProcurementActivities activities =
            Workflow.newActivityStub(
                    B2BProcurementActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(60))
                            .build());

    @Override
    public B2BProcurementAgent.NegotiationAnalysis negotiateRestock(
            String itemId, String itemName, double basePrice, String wholesalerName) {
        return activities.callLlmNegotiation(itemId, itemName, basePrice, wholesalerName);
    }
}
