package ch.swissqcommerce.backend.domain.agent.core.service;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class B2BProcurementWorkflowImpl implements B2BProcurementWorkflow {

    private final B2BProcurementActivities activities =
            Workflow.newActivityStub(
                    B2BProcurementActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(60))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setMaximumAttempts(3)
                                            .setBackoffCoefficient(1.0)
                                            .build())
                            .build());

    private boolean approved = false;
    private boolean rejected = false;
    private boolean adjusted = false;
    private double adjustedPrice = 0.0;
    private String operatorName = "";
    private String decisionReason = "";

    @Override
    public B2BProcurementAgent.NegotiationAnalysis negotiateRestock(
            String itemId, String itemName, double basePrice, String wholesalerName, int quantity) {

        // 1. Call LLM activity
        B2BProcurementAgent.NegotiationAnalysis analysis =
                activities.callLlmNegotiation(itemId, itemName, basePrice, wholesalerName);

        // 2. Validate proposed price against guardrails
        boolean isApproved = activities.checkGuardrail(analysis.proposedPrice, basePrice, quantity);

        if (isApproved) {
            // 4. If passing: automatically create a completed database order
            activities.createFulfilledOrder(
                    itemId, wholesalerName, analysis.proposedPrice, quantity);
        } else {
            // 3. If failing guardrails:
            // - Create a pending database order
            int orderId =
                    activities.createPendingOrder(
                            itemId, wholesalerName, analysis.proposedPrice, quantity);

            // - Create a pending governance audit ticket
            activities.auditNegotiation(orderId, wholesalerName, analysis.proposedPrice, quantity);

            // - Call Workflow.await(...) waiting for supervisor signals
            Workflow.await(() -> approved || rejected || adjusted);

            // - Upon wake-up, execute final state changes and return the outcome
            if (approved) {
                activities.updateOrderStatus(orderId, "fulfilled");
                activities.updateApprovalStatus(orderId, "APPROVED", operatorName, decisionReason);
            } else if (rejected) {
                activities.updateOrderStatus(orderId, "failed");
                activities.updateApprovalStatus(orderId, "REJECTED", operatorName, decisionReason);
                analysis.wholesalerResponse = "REJECTED";
            } else if (adjusted) {
                activities.updateOrderStatus(orderId, "fulfilled");
                activities.updateOrderPrice(orderId, adjustedPrice);
                activities.updateApprovalStatus(
                        orderId,
                        "APPROVED",
                        operatorName,
                        "Adjusted bid to " + adjustedPrice + " CHF: " + decisionReason);
                analysis.proposedPrice = adjustedPrice;
            }
        }

        return analysis;
    }

    @Override
    public void approve(String operator, String reason) {
        this.approved = true;
        this.operatorName = operator;
        this.decisionReason = reason;
    }

    @Override
    public void reject(String operator, String reason) {
        this.rejected = true;
        this.operatorName = operator;
        this.decisionReason = reason;
    }

    @Override
    public void adjustBid(double newPrice, String operator, String reason) {
        this.adjusted = true;
        this.adjustedPrice = newPrice;
        this.operatorName = operator;
        this.decisionReason = reason;
    }
}
