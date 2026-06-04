package com.platform.core.checkout.domain;

public enum PaymentStatus {
    INITIATED,
    BALANCE_CHECKED,
    FRAUD_SCREENED,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED
}
