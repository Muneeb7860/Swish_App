package ch.swissqcommerce.backend.domain.payment.port.out;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentPort {
    Optional<Payment> findById(Integer paymentId);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    Payment save(Payment payment);
}
