package ch.swissqcommerce.backend.domain.agent.core.service;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class B2BProcurementAgent {

    private final WorkflowClient workflowClient;

    public NegotiationAnalysis negotiateRestock(String itemId, String itemName, double basePrice, String wholesalerName) {
        B2BProcurementWorkflow workflow = workflowClient.newWorkflowStub(
                B2BProcurementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("restock-" + itemId + "-" + UUID.randomUUID().toString().substring(0, 8))
                        .setTaskQueue("B2B_PROCUREMENT_TASK_QUEUE")
                        .build()
        );
        return workflow.negotiateRestock(itemId, itemName, basePrice, wholesalerName);
    }

    public static class NegotiationAnalysis {
        public double proposedPrice;
        public double confidence;
        public String rationale;
        public String wholesalerResponse;
        public double cost;

        public NegotiationAnalysis() {
        }

        public NegotiationAnalysis(double proposedPrice, double confidence, String rationale, String wholesalerResponse, double cost) {
            this.proposedPrice = proposedPrice;
            this.confidence = confidence;
            this.rationale = rationale;
            this.wholesalerResponse = wholesalerResponse;
            this.cost = cost;
        }
    }
}
