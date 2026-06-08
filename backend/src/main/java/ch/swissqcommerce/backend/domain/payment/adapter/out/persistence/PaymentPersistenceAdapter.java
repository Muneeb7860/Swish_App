package ch.swissqcommerce.backend.domain.payment.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.adapter.out.persistence.PaymentEntity;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentPort;
import ch.swissqcommerce.backend.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PaymentPersistenceAdapter implements PaymentPort {

    private final PaymentRepository paymentRepository;

    public PaymentPersistenceAdapter(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Optional<Payment> findById(Integer paymentId) {
        return paymentRepository.findById(paymentId)
                .map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::toDomain);
    }

    @Override
    public List<Payment> findByCustomerIdOrderByCreatedAtDesc(String customerId) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = toEntity(payment);
        PaymentEntity saved = paymentRepository.save(entity);
        return toDomain(saved);
    }

    private Payment toDomain(PaymentEntity entity) {
        return Payment.builder()
                .paymentId(entity.getPaymentId())
                .orderId(entity.getOrderId())
                .customerId(entity.getCustomerId())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .paymentMethod(entity.getPaymentMethod())
                .status(entity.getStatus())
                .idempotencyKey(entity.getIdempotencyKey())
                .externalReference(entity.getExternalReference())
                .createdAt(entity.getCreatedAt())
                .capturedAt(entity.getCapturedAt())
                .refundedAt(entity.getRefundedAt())
                .build();
    }

    private PaymentEntity toEntity(Payment domain) {
        return PaymentEntity.builder()
                .paymentId(domain.getPaymentId())
                .orderId(domain.getOrderId())
                .customerId(domain.getCustomerId())
                .amount(domain.getAmount())
                .currency(domain.getCurrency())
                .paymentMethod(domain.getPaymentMethod())
                .status(domain.getStatus())
                .idempotencyKey(domain.getIdempotencyKey())
                .externalReference(domain.getExternalReference())
                .createdAt(domain.getCreatedAt())
                .capturedAt(domain.getCapturedAt())
                .refundedAt(domain.getRefundedAt())
                .build();
    }
}
