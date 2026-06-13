package ch.swissqcommerce.backend.domain.agent.port.in;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentMetrics;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;

public interface AgentUseCase {
    AgentResponse processMessage(AgentRequest request);

    AgentMetrics getMetrics();

    NegotiationResponse negotiateProcurement(NegotiationRequest request);

    class NegotiationRequest {
        private String itemId;
        private String itemName;
        private double basePrice;
        private String wholesalerName;
        private int quantity;
        private String customerId;

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public double getBasePrice() {
            return basePrice;
        }

        public void setBasePrice(double basePrice) {
            this.basePrice = basePrice;
        }

        public String getWholesalerName() {
            return wholesalerName;
        }

        public void setWholesalerName(String wholesalerName) {
            this.wholesalerName = wholesalerName;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
    }

    class NegotiationResponse {
        private boolean approved;
        private String message;
        private double proposedPrice;
        private double confidence;
        private String rationale;
        private String wholesalerResponse;
        private double tokenCost;

        public NegotiationResponse(
                boolean approved,
                String message,
                double proposedPrice,
                double confidence,
                String rationale,
                String wholesalerResponse,
                double tokenCost) {
            this.approved = approved;
            this.message = message;
            this.proposedPrice = proposedPrice;
            this.confidence = confidence;
            this.rationale = rationale;
            this.wholesalerResponse = wholesalerResponse;
            this.tokenCost = tokenCost;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getMessage() {
            return message;
        }

        public double getProposedPrice() {
            return proposedPrice;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getRationale() {
            return rationale;
        }

        public String getWholesalerResponse() {
            return wholesalerResponse;
        }

        public double getTokenCost() {
            return tokenCost;
        }
    }
}
