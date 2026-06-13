package ch.swissqcommerce.backend.domain.agent.port.out;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.HitlQueue;
import java.util.Optional;

public interface AgentOutPort {
    Optional<Customer> findCustomerById(String id);

    Optional<Order> findOrderById(Integer id);

    HitlQueue saveHitlQueue(HitlQueue queue);

    java.util.List<Order> findOrdersByCustomerId(String customerId);
}
