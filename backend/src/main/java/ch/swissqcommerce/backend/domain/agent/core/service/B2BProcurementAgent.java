package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class B2BProcurementAgent {

    private final WorkflowClient workflowClient;
    private final B2BRestockOrderPort restockOrderPort;
    private final DarkStoreRepository darkStoreRepository;
    private final WholesalerPort wholesalerPort;

    public NegotiationAnalysis negotiateRestock(String itemId, String itemName, double basePrice, String wholesalerName) {
        return negotiateRestock(itemId, itemName, basePrice, wholesalerName, 1);
    }

    public NegotiationAnalysis negotiateRestock(String itemId, String itemName, double basePrice, String wholesalerName, int quantity) {
        // Pre-create the restock order in "pending" status to get the restockOrderId
        DarkStore store = darkStoreRepository.findAll().stream().findFirst()
                .orElseGet(() -> DarkStore.builder().storeId("store-1").build());

        Wholesaler wholesaler = wholesalerPort.findAll().stream()
                .filter(w -> w.getName() != null && w.getName().equalsIgnoreCase(wholesalerName))
                .findFirst()
                .orElseGet(() -> wholesalerPort.findAll().stream().findFirst().orElse(null));

        B2BRestockOrder draftOrder = B2BRestockOrder.builder()
                .store(store)
                .wholesaler(wholesaler)
                .invoiceAmount(BigDecimal.ZERO)
                .status("pending")
                .idempotencyKey("RESTOCK-DRAFT-" + UUID.randomUUID().toString())
                .build();

        draftOrder = restockOrderPort.save(draftOrder);
        Integer restockOrderId = draftOrder.getRestockOrderId();

        B2BProcurementWorkflow workflow = workflowClient.newWorkflowStub(
                B2BProcurementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("restock-order-" + restockOrderId)
                        .setTaskQueue("B2B_PROCUREMENT_TASK_QUEUE")
                        .build()
        );
        return workflow.negotiateRestock(itemId, itemName, basePrice, wholesalerName, quantity);
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
