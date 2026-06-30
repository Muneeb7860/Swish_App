package ch.swissqcommerce.backend.domain.payment.port.out;

import java.math.BigDecimal;

/** Outbound port to decouple ledger transaction recording from the payment core domain. */
public interface PaymentLedgerPort {
    void recordPaymentAuth(Integer orderId, String customerId, BigDecimal amount);
}
