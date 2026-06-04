package com.platform.core.checkout.domain;

import com.platform.core.checkout.adapters.PaymentRepository;
import com.platform.core.common.OutboxEntity;
import com.platform.core.common.OutboxRepository;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;

    public PaymentService(PaymentRepository paymentRepository, OutboxRepository outboxRepository) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public Payment processCheckoutPayment(String idempotencyKey, String customerId, String orderId, BigDecimal amount) {
        // Idempotency check at DB level (fallback if Redis misses)
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            return existingPayment.get();
        }

        Payment payment = new Payment(idempotencyKey, customerId, orderId, amount, PaymentStatus.INITIATED);
        payment = paymentRepository.save(payment);

        // Transactional Outbox pattern: Save event in the same DB transaction
        String payload = "{\"paymentId\": " + payment.getId() + ", \"status\": \"INITIATED\"}";
        OutboxEntity outboxEvent = new OutboxEntity("Payment", String.valueOf(payment.getId()), "PaymentInitiated", payload);
        outboxRepository.save(outboxEvent);

        return payment;
    }
}
