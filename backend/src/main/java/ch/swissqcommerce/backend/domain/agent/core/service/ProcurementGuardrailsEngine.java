package ch.swissqcommerce.backend.domain.agent.core.service;

import org.springframework.stereotype.Service;

@Service
public class ProcurementGuardrailsEngine {

    private static final double MAX_ORDER_AMOUNT_LIMIT = 5000.0;
    private static final double MAX_PRICE_VARIANCE_TOLERANCE = 0.10; // 10%

    public GuardrailResult validate(double proposedPrice, double baseWholesalePrice, int quantity) {
        double totalAmount = proposedPrice * quantity;

        // 1. Absolute total spending limit check
        if (totalAmount > MAX_ORDER_AMOUNT_LIMIT) {
            return new GuardrailResult(
                    false,
                    "Total order amount ("
                            + totalAmount
                            + " CHF) exceeds maximum spend limit of "
                            + MAX_ORDER_AMOUNT_LIMIT
                            + " CHF. Esculating to HITL Queue.");
        }

        // 2. Pricing variance tolerance check
        double priceDiff = baseWholesalePrice - proposedPrice;
        if (priceDiff < 0) {
            return new GuardrailResult(
                    false,
                    "Proposed price ("
                            + proposedPrice
                            + " CHF) is higher than base wholesale price ("
                            + baseWholesalePrice
                            + " CHF). Potential AI hallucination. Esculating to HITL.");
        }

        double percentVariance = priceDiff / baseWholesalePrice;
        if (percentVariance > MAX_PRICE_VARIANCE_TOLERANCE) {
            return new GuardrailResult(
                    false,
                    "Proposed discount price ("
                            + proposedPrice
                            + " CHF) represents a "
                            + String.format("%.2f", percentVariance * 100)
                            + "% variance, exceeding the "
                            + (MAX_PRICE_VARIANCE_TOLERANCE * 100)
                            + "% maximum variance tolerance. Esculating to HITL Queue.");
        }

        return new GuardrailResult(
                true, "Transaction passed guardrail checks successfully. Auto-approving.");
    }

    public static class GuardrailResult {
        private final boolean approved;
        private final String message;

        public GuardrailResult(boolean approved, String message) {
            this.approved = approved;
            this.message = message;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getMessage() {
            return message;
        }
    }
}
