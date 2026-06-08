package ch.swissqcommerce.backend.domain.payment.core.service;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentPort;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.OutboxEventPort;
import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class PaymentUseCaseImpl implements PaymentUseCase {

    private final PaymentPort paymentPort;
    private final ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort orderPort;
    private final LedgerUseCase ledgerUseCase;
    private final OutboxEventPort outboxEventPort;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentUseCaseImpl(PaymentPort paymentPort,
                              ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort orderPort,
                              LedgerUseCase ledgerUseCase,
                              OutboxEventPort outboxEventPort,
                              ApplicationEventPublisher eventPublisher) {
        this.paymentPort = paymentPort;
        this.orderPort = orderPort;
        this.ledgerUseCase = ledgerUseCase;
        this.outboxEventPort = outboxEventPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Payment authorizePayment(Integer orderId, String customerId, BigDecimal amount, String paymentMethod, String idempotencyKey) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID is required to initialize payment.");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID is required to initialize payment.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method is required.");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return paymentPort.findByIdempotencyKey(idempotencyKey).orElseGet(() -> createAuthorization(orderId, customerId, amount, paymentMethod, idempotencyKey));
        }

        return createAuthorization(orderId, customerId, amount, paymentMethod, idempotencyKey);
    }

    private Payment createAuthorization(Integer orderId, String customerId, BigDecimal amount, String paymentMethod, String idempotencyKey) {
        Order order = orderPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getCustomer() == null || !customerId.equals(order.getCustomer().getCustomerId())) {
            throw new IllegalArgumentException("Payment customer must match order customer.");
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .currency("CHF")
                .paymentMethod(paymentMethod)
                .status("AUTHORIZED")
                .idempotencyKey(idempotencyKey)
                .build();

        ledgerUseCase.recordTransaction(
                "PAYMENT-AUTH",
                "Authorise payment for order " + orderId,
                List.of(
                        new LedgerUseCase.LedgerLeg("customer", customerId, amount, BigDecimal.ZERO),
                        new LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, amount)
                )
        );

        Payment saved = paymentPort.save(payment);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("Payment")
                .aggregateId(saved.getPaymentId() != null ? saved.getPaymentId().toString() : null)
                .eventType("payment.authorized")
                .payload(String.format("{\"paymentId\": %d, \"orderId\": %d, \"amount\": %s}",
                        saved.getPaymentId(), orderId, saved.getAmount()))
                .build();
        outboxEventPort.save(event);
        eventPublisher.publishEvent(event);

        OutboxEvent fraudEvent = OutboxEvent.builder()
                .aggregateType("Payment")
                .aggregateId(saved.getPaymentId() != null ? saved.getPaymentId().toString() : null)
                .eventType("payment.fraud_check")
                .payload(String.format("{\"paymentId\": %d, \"orderId\": %d, \"amount\": %s, \"customerId\": \"%s\"}",
                        saved.getPaymentId(), orderId, saved.getAmount(), customerId))
                .build();
        outboxEventPort.save(fraudEvent);

        return saved;
    }

    @Override
    @Transactional
    public Payment capturePayment(Integer paymentId) {
        Payment payment = paymentPort.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (!"AUTHORIZED".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalStateException("Only authorized payments can be captured.");
        }

        payment.setStatus("CAPTURED");
        payment.setCapturedAt(OffsetDateTime.now());
        Payment saved = paymentPort.save(payment);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("Payment")
                .aggregateId(saved.getPaymentId().toString())
                .eventType("payment.captured")
                .payload(String.format("{\"paymentId\": %d, \"orderId\": %d, \"amount\": %s}",
                        saved.getPaymentId(), saved.getOrderId(), saved.getAmount()))
                .build();
        outboxEventPort.save(event);
        eventPublisher.publishEvent(event);

        OutboxEvent notificationEvent = OutboxEvent.builder()
                .aggregateType("Payment")
                .aggregateId(saved.getPaymentId().toString())
                .eventType("payment.notification")
                .payload(String.format("{\"paymentId\": %d, \"orderId\": %d, \"status\": \"CAPTURED\"}",
                        saved.getPaymentId(), saved.getOrderId()))
                .build();
        outboxEventPort.save(notificationEvent);

        return saved;
    }

    @Override
    public Payment getPayment(Integer paymentId) {
        return paymentPort.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }

    @Override
    public List<Payment> getPaymentsByCustomer(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID is required to query payments.");
        }
        return paymentPort.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
