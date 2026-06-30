package ch.swissqcommerce.backend.domain.payment.core.service;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
import ch.swissqcommerce.backend.domain.payment.port.out.OrderValidationPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentEventPublisherPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentLedgerPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentPort;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class PaymentUseCaseImpl implements PaymentUseCase {

    private final PaymentPort paymentPort;
    private final OrderValidationPort orderValidationPort;
    private final PaymentLedgerPort paymentLedgerPort;
    private final PaymentEventPublisherPort paymentEventPublisherPort;

    public PaymentUseCaseImpl(
            PaymentPort paymentPort,
            OrderValidationPort orderValidationPort,
            PaymentLedgerPort paymentLedgerPort,
            PaymentEventPublisherPort paymentEventPublisherPort) {
        this.paymentPort = paymentPort;
        this.orderValidationPort = orderValidationPort;
        this.paymentLedgerPort = paymentLedgerPort;
        this.paymentEventPublisherPort = paymentEventPublisherPort;
    }

    @Override
    @Transactional
    public Payment authorizePayment(
            Integer orderId,
            String customerId,
            BigDecimal amount,
            String paymentMethod,
            String idempotencyKey) {
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
            return paymentPort
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseGet(
                            () ->
                                    createAuthorization(
                                            orderId,
                                            customerId,
                                            amount,
                                            paymentMethod,
                                            idempotencyKey));
        }

        return createAuthorization(orderId, customerId, amount, paymentMethod, idempotencyKey);
    }

    private Payment createAuthorization(
            Integer orderId,
            String customerId,
            BigDecimal amount,
            String paymentMethod,
            String idempotencyKey) {

        // Validate that order matches customer
        orderValidationPort.validateOrderCustomer(orderId, customerId);

        Payment payment =
                Payment.builder()
                        .orderId(orderId)
                        .customerId(customerId)
                        .amount(amount)
                        .currency("CHF")
                        .paymentMethod(paymentMethod)
                        .status("AUTHORIZED")
                        .idempotencyKey(idempotencyKey)
                        .build();

        // Record authorization ledger transaction
        paymentLedgerPort.recordPaymentAuth(orderId, customerId, amount);

        Payment saved = paymentPort.save(payment);

        // Publish outbox events
        paymentEventPublisherPort.publishPaymentAuthorized(
                saved.getPaymentId(), orderId, saved.getAmount());
        paymentEventPublisherPort.publishPaymentFraudCheck(
                saved.getPaymentId(), orderId, saved.getAmount(), customerId);

        return saved;
    }

    @Override
    @Transactional
    public Payment capturePayment(Integer paymentId) {
        Payment payment =
                paymentPort
                        .findById(paymentId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Payment not found: " + paymentId));

        if (!"AUTHORIZED".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalStateException("Only authorized payments can be captured.");
        }

        payment.setStatus("CAPTURED");
        payment.setCapturedAt(OffsetDateTime.now());
        Payment saved = paymentPort.save(payment);

        // Publish capture outbox events
        paymentEventPublisherPort.publishPaymentCaptured(
                saved.getPaymentId(), saved.getOrderId(), saved.getAmount());
        paymentEventPublisherPort.publishPaymentNotification(
                saved.getPaymentId(), saved.getOrderId(), saved.getAmount(), "CAPTURED");

        return saved;
    }

    @Override
    public Payment getPayment(Integer paymentId) {
        return paymentPort
                .findById(paymentId)
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
