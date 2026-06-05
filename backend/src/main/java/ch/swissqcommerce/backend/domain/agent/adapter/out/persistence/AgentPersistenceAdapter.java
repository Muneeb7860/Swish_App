package ch.swissqcommerce.backend.domain.agent.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.repository.OrderRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AgentPersistenceAdapter implements AgentOutPort {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final HitlQueueRepository hitlQueueRepository;

    public AgentPersistenceAdapter(CustomerRepository customerRepository,
                                   OrderRepository orderRepository,
                                   HitlQueueRepository hitlQueueRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.hitlQueueRepository = hitlQueueRepository;
    }

    @Override
    public Optional<Customer> findCustomerById(String id) {
        return customerRepository.findById(id);
    }

    @Override
    public Optional<Order> findOrderById(Integer id) {
        return orderRepository.findById(id);
    }

    @Override
    public HitlQueue saveHitlQueue(HitlQueue queue) {
        return hitlQueueRepository.save(queue);
    }

    @Override
    public java.util.List<Order> findOrdersByCustomerId(String customerId) {
        return orderRepository.findByCustomerCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
