package ch.swissqcommerce.backend.domain.agent.adapter.in.web;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentMetrics;
import ch.swissqcommerce.backend.domain.agent.port.in.AgentUseCase;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.Wholesaler;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.model.B2BRestockOrder;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.repository.WholesalerRepository;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.repository.B2BRestockOrderRepository;
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
    private WholesalerRepository wholesalerRepository;

    @Autowired
    private DarkStoreRepository darkStoreRepository;

    @Autowired
    private B2BRestockOrderRepository restockOrderRepository;

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
        var analysis = b2BProcurementAgent.negotiateRestock(
                request.getItemId(), request.getItemName(), request.getBasePrice(), request.getWholesalerName());
        
        var guardrailResult = procurementGuardrailsEngine.validate(
                analysis.proposedPrice, request.getBasePrice(), request.getQuantity());

        if (!guardrailResult.isApproved()) {
            Wholesaler wholesaler = wholesalerRepository.findById(request.getWholesalerName())
                    .or(() -> wholesalerRepository.findAll().stream()
                            .filter(w -> w.getName().equalsIgnoreCase(request.getWholesalerName()))
                            .findFirst())
                    .orElseGet(() -> wholesalerRepository.findByIsPrimary(true).orElse(null));

            DarkStore store = darkStoreRepository.findAll().stream().findFirst()
                    .orElseGet(() -> DarkStore.builder().storeId("store-1").build());

            BigDecimal orderAmount = BigDecimal.valueOf(analysis.proposedPrice * request.getQuantity());

            B2BRestockOrder restockOrder = B2BRestockOrder.builder()
                    .store(store)
                    .wholesaler(wholesaler)
                    .invoiceAmount(orderAmount)
                    .status("pending")
                    .idempotencyKey("RESTOCK-" + UUID.randomUUID().toString())
                    .build();

            restockOrder = restockOrderRepository.save(restockOrder);

            String wholesalerId = wholesaler != null ? wholesaler.getWholesalerId() : "WHOLESALER-1";
            governanceUseCase.auditNegotiation(restockOrder.getRestockOrderId(), wholesalerId, orderAmount);
        }

        NegotiationResponse response = new NegotiationResponse(
                guardrailResult.isApproved(),
                guardrailResult.getMessage(),
                analysis.proposedPrice,
                analysis.confidence,
                analysis.rationale,
                analysis.wholesalerResponse,
                analysis.cost
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

