package ch.swissqcommerce.backend.domain.payment.adapter.out.integration;

import ch.swissqcommerce.backend.domain.payment.port.out.PaymentEventPublisherPort;
import ch.swissqcommerce.backend.domain.transaction.port.out.OutboxEventPort;
import ch.swissqcommerce.backend.model.OutboxEvent;
import java.math.BigDecimal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisherAdapter implements PaymentEventPublisherPort {

    private final OutboxEventPort outboxEventPort;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentEventPublisherAdapter(
            OutboxEventPort outboxEventPort, ApplicationEventPublisher eventPublisher) {
        this.outboxEventPort = outboxEventPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publishPaymentAuthorized(Integer paymentId, Integer orderId, BigDecimal amount) {
        OutboxEvent event =
                OutboxEvent.builder()
                        .aggregateType("Payment")
                        .aggregateId(paymentId != null ? paymentId.toString() : null)
                        .eventType("payment.authorized")
                        .payload(
                                String.format(
                                        "{\"paymentId\": %d, \"orderId\": %d, \"amount\": %s}",
                                        paymentId, orderId, amount))
                        .build();
        outboxEventPort.save(event);
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishPaymentFraudCheck(
            Integer paymentId, Integer orderId, BigDecimal amount, String customerId) {
        OutboxEvent event =
                OutboxEvent.builder()
                        .aggregateType("Payment")
                        .aggregateId(paymentId != null ? paymentId.toString() : null)
                        .eventType("payment.fraud_check")
                        .payload(
                                String.format(
                                        "{\"paymentId\": %d, \"orderId\": %d, \"amount\": %s,"
                                                + " \"customerId\": \"%s\"}",
                                        paymentId, orderId, amount, customerId))
                        .build();
        outboxEventPort.save(event);
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishPaymentCaptured(Integer paymentId, Integer orderId, BigDecimal amount) {
        OutboxEvent event =
                OutboxEvent.builder()
                        .aggregateType("Payment")
                        .aggregateId(paymentId != null ? paymentId.toString() : null)
                        .eventType("payment.captured")
                        .payload(
                                String.format(
                                        "{\"paymentId\": %d, \"orderId\": %d, \"amount\": %s}",
                                        paymentId, orderId, amount))
                        .build();
        outboxEventPort.save(event);
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishPaymentNotification(
            Integer paymentId, Integer orderId, BigDecimal amount, String status) {
        OutboxEvent event =
                OutboxEvent.builder()
                        .aggregateType("Payment")
                        .aggregateId(paymentId != null ? paymentId.toString() : null)
                        .eventType("payment.notification")
                        .payload(
                                String.format(
                                        "{\"paymentId\": %d, \"orderId\": %d, \"status\": \"%s\","
                                                + " \"amount\": %s}",
                                        paymentId, orderId, status, amount))
                        .build();
        outboxEventPort.save(event);
        eventPublisher.publishEvent(event);
    }
}
