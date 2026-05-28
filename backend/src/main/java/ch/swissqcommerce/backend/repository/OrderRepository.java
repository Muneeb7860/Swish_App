package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByCustomerCustomerIdOrderByCreatedAtDesc(String customerId);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}

