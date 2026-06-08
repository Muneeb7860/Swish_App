package ch.swissqcommerce.backend.domain.agent.adapter.out.persistence;
import java.util.List;


import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AgentPersistenceAdapter implements AgentOutPort {

    private final CustomerRepository customerRepository;
    private final OrderPort orderPort;
    private final HitlQueueRepository hitlQueueRepository;

    public AgentPersistenceAdapter(CustomerRepository customerRepository,
                                   OrderPort orderPort,
                                   HitlQueueRepository hitlQueueRepository) {
        this.customerRepository = customerRepository;
        this.orderPort = orderPort;
        this.hitlQueueRepository = hitlQueueRepository;
    }

    @Override
    public Optional<Customer> findCustomerById(String id) {
        return customerRepository.findById(id);
    }

    @Override
    public Optional<Order> findOrderById(Integer id) {
        return orderPort.findById(id);
    }

    @Override
    public HitlQueue saveHitlQueue(HitlQueue queue) {
        return hitlQueueRepository.save(queue);
    }

    @Override
    public java.util.List<Order> findOrdersByCustomerId(String customerId) {
        return orderPort.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
