package ch.swissqcommerce.backend.domain.transaction.port.out;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import java.util.List;
import java.util.Optional;

public interface OrderPort {
    Optional<Order> findById(Integer id);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Order save(Order order);

    void flush();

    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Order> findAllById(List<Integer> ids);
}
