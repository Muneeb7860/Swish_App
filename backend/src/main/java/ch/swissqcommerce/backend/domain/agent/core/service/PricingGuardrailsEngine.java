package ch.swissqcommerce.backend.domain.agent.core.service;

import org.springframework.stereotype.Service;

@Service
public class PricingGuardrailsEngine {

    private static final double MIN_SURGE_MULTIPLIER = 1.0;
    private static final double MAX_SURGE_MULTIPLIER = 3.0;
    private static final double MIN_DISCOUNT_PERCENT = 0.0;
    private static final double MAX_DISCOUNT_PERCENT = 50.0;

    public GuardrailResult validateAndClamp(double surgeMultiplier, double discountPercent) {
        boolean adjusted = false;
        double finalSurge = surgeMultiplier;
        double finalDiscount = discountPercent;
        StringBuilder message = new StringBuilder("Pricing values validated.");

        // 1. Validate and clamp Surge Multiplier
        if (surgeMultiplier < MIN_SURGE_MULTIPLIER) {
            finalSurge = MIN_SURGE_MULTIPLIER;
            adjusted = true;
            message.append(" Surge multiplier raised to minimum ").append(MIN_SURGE_MULTIPLIER).append(".");
        } else if (surgeMultiplier > MAX_SURGE_MULTIPLIER) {
            finalSurge = MAX_SURGE_MULTIPLIER;
            adjusted = true;
            message.append(" Surge multiplier clamped to maximum ").append(MAX_SURGE_MULTIPLIER).append(".");
        }

        // 2. Validate and clamp Discount Percent
        if (discountPercent < MIN_DISCOUNT_PERCENT) {
            finalDiscount = MIN_DISCOUNT_PERCENT;
            adjusted = true;
            message.append(" Perishable discount raised to minimum ").append(MIN_DISCOUNT_PERCENT).append("%.");
        } else if (discountPercent > MAX_DISCOUNT_PERCENT) {
            finalDiscount = MAX_DISCOUNT_PERCENT;
            adjusted = true;
            message.append(" Perishable discount clamped to maximum ").append(MAX_DISCOUNT_PERCENT).append("%.");
        }

        return new GuardrailResult(true, adjusted, finalSurge, finalDiscount, message.toString().trim());
    }

    public static class GuardrailResult {
        private final boolean approved;
        private final boolean adjusted;
        private final double validatedSurgeMultiplier;
        private final double validatedDiscountPercent;
        private final String message;

        public GuardrailResult(boolean approved, boolean adjusted, double validatedSurgeMultiplier, double validatedDiscountPercent, String message) {
            this.approved = approved;
            this.adjusted = adjusted;
            this.validatedSurgeMultiplier = validatedSurgeMultiplier;
            this.validatedDiscountPercent = validatedDiscountPercent;
            this.message = message;
        }

        public boolean isApproved() { return approved; }
        public boolean isAdjusted() { return adjusted; }
        public double getValidatedSurgeMultiplier() { return validatedSurgeMultiplier; }
        public double getValidatedDiscountPercent() { return validatedDiscountPercent; }
        public String getMessage() { return message; }
    }
}
