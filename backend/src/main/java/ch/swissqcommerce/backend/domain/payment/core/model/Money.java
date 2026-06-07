package ch.swissqcommerce.backend.domain.payment.core.model;

import lombok.Value;
import java.math.BigDecimal;

@Value
public class Money {
    BigDecimal amount;
    String currency;

    public Money(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative or null");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be empty");
        }
        this.amount = amount;
        this.currency = currency;
    }
}
