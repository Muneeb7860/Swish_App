package ch.swissqcommerce.backend.domain.payment.port.out;

import java.math.BigDecimal;

/** Outbound port to decouple outbox event publishing from the payment core domain. */
public interface PaymentEventPublisherPort {
    void publishPaymentAuthorized(Integer paymentId, Integer orderId, BigDecimal amount);

    void publishPaymentFraudCheck(
            Integer paymentId, Integer orderId, BigDecimal amount, String customerId);

    void publishPaymentCaptured(Integer paymentId, Integer orderId, BigDecimal amount);

    void publishPaymentNotification(
            Integer paymentId, Integer orderId, BigDecimal amount, String status);
}
