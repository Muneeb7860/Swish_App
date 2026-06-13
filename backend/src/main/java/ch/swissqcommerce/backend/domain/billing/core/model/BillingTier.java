package ch.swissqcommerce.backend.domain.billing.core.model;

import java.math.BigDecimal;

/**
 * Flat-tier subscription plans (BRD FR-06). Each tier carries a flat monthly fee that becomes the
 * invoice amount for a billing period, independent of usage.
 */
public enum BillingTier {
    BASIC(new BigDecimal("99.00")),
    PRO(new BigDecimal("249.00")),
    ENTERPRISE(new BigDecimal("599.00"));

    private final BigDecimal monthlyFee;

    BillingTier(BigDecimal monthlyFee) {
        this.monthlyFee = monthlyFee;
    }

    public BigDecimal getMonthlyFee() {
        return monthlyFee;
    }
}
