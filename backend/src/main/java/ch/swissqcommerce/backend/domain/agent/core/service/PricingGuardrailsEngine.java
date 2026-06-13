package ch.swissqcommerce.backend.domain.agent.core.service;

import org.springframework.stereotype.Service;

@Service
public class PricingGuardrailsEngine {

    private static final double MIN_SURGE_MULTIPLIER = 1.0;
    private static final double MAX_SURGE_MULTIPLIER = 3.0;
    private static final double MIN_DISCOUNT_PERCENT = 0.0;
    private static final double MAX_DISCOUNT_PERCENT = 50.0;

    // Phase 8 review thresholds: values within the clamp range but aggressive
    // enough to warrant a supervisor's eyes. Tripping these still returns a
    // (clamped) recommendation, but also flags the decision to the HITL queue.
    private static final double REVIEW_SURGE_THRESHOLD = 2.5;
    private static final double REVIEW_DISCOUNT_THRESHOLD = 15.0;

    public GuardrailResult validateAndClamp(double surgeMultiplier, double discountPercent) {
        boolean adjusted = false;
        double finalSurge = surgeMultiplier;
        double finalDiscount = discountPercent;
        StringBuilder message = new StringBuilder("Pricing values validated.");

        // 1. Validate and clamp Surge Multiplier
        if (surgeMultiplier < MIN_SURGE_MULTIPLIER) {
            finalSurge = MIN_SURGE_MULTIPLIER;
            adjusted = true;
            message.append(" Surge multiplier raised to minimum ")
                    .append(MIN_SURGE_MULTIPLIER)
                    .append(".");
        } else if (surgeMultiplier > MAX_SURGE_MULTIPLIER) {
            finalSurge = MAX_SURGE_MULTIPLIER;
            adjusted = true;
            message.append(" Surge multiplier clamped to maximum ")
                    .append(MAX_SURGE_MULTIPLIER)
                    .append(".");
        }

        // 2. Validate and clamp Discount Percent
        if (discountPercent < MIN_DISCOUNT_PERCENT) {
            finalDiscount = MIN_DISCOUNT_PERCENT;
            adjusted = true;
            message.append(" Perishable discount raised to minimum ")
                    .append(MIN_DISCOUNT_PERCENT)
                    .append("%.");
        } else if (discountPercent > MAX_DISCOUNT_PERCENT) {
            finalDiscount = MAX_DISCOUNT_PERCENT;
            adjusted = true;
            message.append(" Perishable discount clamped to maximum ")
                    .append(MAX_DISCOUNT_PERCENT)
                    .append("%.");
        }

        // 3. Flag aggressive (but in-range) values for human review. Evaluated on the
        //    model's ORIGINAL proposal, not the clamped value.
        boolean needsReview = false;
        StringBuilder review = new StringBuilder();
        if (surgeMultiplier > REVIEW_SURGE_THRESHOLD) {
            needsReview = true;
            review.append(
                    String.format(
                            "Surge %.2fx exceeds review threshold %.1fx. ",
                            surgeMultiplier, REVIEW_SURGE_THRESHOLD));
        }
        if (discountPercent > REVIEW_DISCOUNT_THRESHOLD) {
            needsReview = true;
            review.append(
                    String.format(
                            "Discount %.1f%% exceeds review threshold %.1f%%. ",
                            discountPercent, REVIEW_DISCOUNT_THRESHOLD));
        }

        return new GuardrailResult(
                true,
                adjusted,
                finalSurge,
                finalDiscount,
                message.toString().trim(),
                needsReview,
                review.toString().trim());
    }

    public static class GuardrailResult {
        private final boolean approved;
        private final boolean adjusted;
        private final double validatedSurgeMultiplier;
        private final double validatedDiscountPercent;
        private final String message;
        private final boolean needsReview;
        private final String reviewReason;

        public GuardrailResult(
                boolean approved,
                boolean adjusted,
                double validatedSurgeMultiplier,
                double validatedDiscountPercent,
                String message) {
            this(
                    approved,
                    adjusted,
                    validatedSurgeMultiplier,
                    validatedDiscountPercent,
                    message,
                    false,
                    "");
        }

        public GuardrailResult(
                boolean approved,
                boolean adjusted,
                double validatedSurgeMultiplier,
                double validatedDiscountPercent,
                String message,
                boolean needsReview,
                String reviewReason) {
            this.approved = approved;
            this.adjusted = adjusted;
            this.validatedSurgeMultiplier = validatedSurgeMultiplier;
            this.validatedDiscountPercent = validatedDiscountPercent;
            this.message = message;
            this.needsReview = needsReview;
            this.reviewReason = reviewReason;
        }

        public boolean isApproved() {
            return approved;
        }

        public boolean isAdjusted() {
            return adjusted;
        }

        public double getValidatedSurgeMultiplier() {
            return validatedSurgeMultiplier;
        }

        public double getValidatedDiscountPercent() {
            return validatedDiscountPercent;
        }

        public String getMessage() {
            return message;
        }

        public boolean isNeedsReview() {
            return needsReview;
        }

        public String getReviewReason() {
            return reviewReason;
        }
    }
}
