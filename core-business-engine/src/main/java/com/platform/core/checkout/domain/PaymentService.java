package com.platform.core.checkout.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.checkout.adapters.PaymentRepository;
import com.platform.core.common.OutboxEntity;
import com.platform.core.common.OutboxRepository;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // Fix #15: Use Jackson ObjectMapper for safe JSON serialization
        String payload = serializePayload(Map.of(
                "paymentId", payment.getId(),
                "customerId", customerId,
                "orderId", orderId,
                "status", "INITIATED"
        ));
        OutboxEntity outboxEvent = new OutboxEntity("Payment", String.valueOf(payment.getId()), "PaymentInitiated", payload);
        outboxRepository.save(outboxEvent);

        return payment;
    }

    /**
     * Fix #19: Called by PaymentWebhookController when Stripe confirms payment.
     */
    @Transactional
    public void confirmPayment(String paymentIntentId) {
        // In production, look up by Stripe paymentIntentId. For MVP, simulate.
        // paymentRepository.findByPaymentIntentId(paymentIntentId)...
        
        String payload = serializePayload(Map.of(
                "paymentIntentId", paymentIntentId,
                "status", "CONFIRMED",
                "type", "PAYMENT_CONFIRMED"
        ));
        OutboxEntity outboxEvent = new OutboxEntity("Payment", paymentIntentId, "PaymentConfirmed", payload);
        outboxRepository.save(outboxEvent);
    }

    /**
     * Fix #19: Called by PaymentWebhookController when Stripe reports failure.
     */
    @Transactional
    public void failPayment(String paymentIntentId) {
        String payload = serializePayload(Map.of(
                "paymentIntentId", paymentIntentId,
                "status", "FAILED",
                "type", "PAYMENT_FAILED"
        ));
        OutboxEntity outboxEvent = new OutboxEntity("Payment", paymentIntentId, "PaymentFailed", payload);
        outboxRepository.save(outboxEvent);
    }

    private String serializePayload(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
