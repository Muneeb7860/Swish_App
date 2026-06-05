package ch.swissqcommerce.backend.domain.agent.adapter.in.web;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentMetrics;
import ch.swissqcommerce.backend.domain.agent.port.in.AgentUseCase;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private AgentUseCase agentUseCase;

    @Autowired
    private ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementAgent b2BProcurementAgent;

    @Autowired
    private ch.swissqcommerce.backend.domain.agent.core.service.ProcurementGuardrailsEngine procurementGuardrailsEngine;

    @Autowired
    private HitlQueueRepository hitlQueueRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WholesalerPort wholesalerPort;

    @Autowired
    private DarkStoreRepository darkStoreRepository;

    @Autowired
    private B2BRestockOrderPort restockOrderPort;

    @Autowired
    private GovernanceUseCase governanceUseCase;

    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request) {
        AgentResponse response = agentUseCase.processMessage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<AgentMetrics> getMetrics() {
        return ResponseEntity.ok(agentUseCase.getMetrics());
    }

    @PostMapping("/negotiate")
    public ResponseEntity<NegotiationResponse> negotiate(@RequestBody NegotiationRequest request) {
        java.util.List<Wholesaler> activeWholesalers = wholesalerPort.findAll().stream()
                .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
                .toList();

        if (activeWholesalers.isEmpty()) {
            return ResponseEntity.ok(new NegotiationResponse(
                    false,
                    "No active wholesalers found.",
                    0.0, 0.0, "N/A", "REJECTED", 0.0
            ));
        }

        ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementAgent.NegotiationAnalysis bestAnalysis = null;
        Wholesaler bestWholesaler = null;

        for (Wholesaler wholesaler : activeWholesalers) {
            var analysis = b2BProcurementAgent.negotiateRestock(
                    request.getItemId(), request.getItemName(), request.getBasePrice(), wholesaler.getName());
            
            if (bestAnalysis == null || analysis.proposedPrice < bestAnalysis.proposedPrice) {
                bestAnalysis = analysis;
                bestWholesaler = wholesaler;
            } else if (analysis.proposedPrice == bestAnalysis.proposedPrice) {
                if (bestWholesaler != null && wholesaler.getTrustScore() > bestWholesaler.getTrustScore()) {
                    bestAnalysis = analysis;
                    bestWholesaler = wholesaler;
                }
            }
        }

        var guardrailResult = procurementGuardrailsEngine.validate(
                bestAnalysis.proposedPrice, request.getBasePrice(), request.getQuantity());

        if (!guardrailResult.isApproved()) {
            DarkStore store = darkStoreRepository.findAll().stream().findFirst()
                    .orElseGet(() -> DarkStore.builder().storeId("store-1").build());

            BigDecimal orderAmount = BigDecimal.valueOf(bestAnalysis.proposedPrice * request.getQuantity());

            B2BRestockOrder restockOrder = B2BRestockOrder.builder()
                    .store(store)
                    .wholesaler(bestWholesaler)
                    .invoiceAmount(orderAmount)
                    .status("pending")
                    .idempotencyKey("RESTOCK-" + UUID.randomUUID().toString())
                    .build();

            restockOrder = restockOrderPort.save(restockOrder);

            String wholesalerId = bestWholesaler != null ? bestWholesaler.getWholesalerId() : "WHOLESALER-1";
            governanceUseCase.auditNegotiation(restockOrder.getRestockOrderId(), wholesalerId, orderAmount);
        }

        String winningMessage = "RFQ AUCTION WINNER: " + bestWholesaler.getName() + " (Bid: " + bestAnalysis.proposedPrice + " CHF). " + guardrailResult.getMessage();

        NegotiationResponse response = new NegotiationResponse(
                guardrailResult.isApproved(),
                winningMessage,
                bestAnalysis.proposedPrice,
                bestAnalysis.confidence,
                bestAnalysis.rationale,
                bestAnalysis.wholesalerResponse,
                bestAnalysis.cost
        );
        return ResponseEntity.ok(response);
    }

    public static class NegotiationRequest {
        private String itemId;
        private String itemName;
        private double basePrice;
        private String wholesalerName;
        private int quantity;
        private String customerId;

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public double getBasePrice() { return basePrice; }
        public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
        public String getWholesalerName() { return wholesalerName; }
        public void setWholesalerName(String wholesalerName) { this.wholesalerName = wholesalerName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
    }

    public static class NegotiationResponse {
        private boolean approved;
        private String message;
        private double proposedPrice;
        private double confidence;
        private String rationale;
        private String wholesalerResponse;
        private double tokenCost;

        public NegotiationResponse(boolean approved, String message, double proposedPrice, double confidence, String rationale, String wholesalerResponse, double tokenCost) {
            this.approved = approved;
            this.message = message;
            this.proposedPrice = proposedPrice;
            this.confidence = confidence;
            this.rationale = rationale;
            this.wholesalerResponse = wholesalerResponse;
            this.tokenCost = tokenCost;
        }

        public boolean isApproved() { return approved; }
        public String getMessage() { return message; }
        public double getProposedPrice() { return proposedPrice; }
        public double getConfidence() { return confidence; }
        public String getRationale() { return rationale; }
        public String getWholesalerResponse() { return wholesalerResponse; }
        public double getTokenCost() { return tokenCost; }
    }
}

