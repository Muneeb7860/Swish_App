package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByCustomerCustomerIdOrderByCreatedAtDesc(String customerId);
}
