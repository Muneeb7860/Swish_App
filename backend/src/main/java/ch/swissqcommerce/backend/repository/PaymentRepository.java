package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.payment.adapter.out.persistence.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Integer> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
    List<PaymentEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
