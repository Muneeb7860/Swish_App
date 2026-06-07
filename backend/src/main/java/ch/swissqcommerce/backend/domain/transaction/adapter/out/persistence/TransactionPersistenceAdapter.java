package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.transaction.port.out.*;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TransactionPersistenceAdapter implements 
        CustomerPort, RiderPort, InventoryPort, DarkStorePort, SystemConfigPort, OutboxEventPort, OrderPort, HitlQueuePort {

    private final CustomerRepository customerRepository;
    private final RiderRepository riderRepository;
    private final InventoryRepository inventoryRepository;
    private final DarkStoreRepository darkStoreRepository;
    private final SystemConfigurationRepository systemConfigurationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final HitlQueueRepository hitlQueueRepository;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    public TransactionPersistenceAdapter(CustomerRepository customerRepository,
                                         RiderRepository riderRepository,
                                         InventoryRepository inventoryRepository,
                                         DarkStoreRepository darkStoreRepository,
                                         SystemConfigurationRepository systemConfigurationRepository,
                                         OutboxEventRepository outboxEventRepository,
                                         OrderRepository orderRepository,
                                         HitlQueueRepository hitlQueueRepository,
                                         org.springframework.context.ApplicationEventPublisher applicationEventPublisher) {
        this.customerRepository = customerRepository;
        this.riderRepository = riderRepository;
        this.inventoryRepository = inventoryRepository;
        this.darkStoreRepository = darkStoreRepository;
        this.systemConfigurationRepository = systemConfigurationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.orderRepository = orderRepository;
        this.hitlQueueRepository = hitlQueueRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public Optional<Customer> findCustomerById(String id) {
        return customerRepository.findById(id);
    }

    @Override
    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }

    @Override
    public List<Rider> findAll() {
        return riderRepository.findAll();
    }

    @Override
    public Optional<Inventory> findInventoryById(String id) {
        return inventoryRepository.findById(id);
    }

    @Override
    public Inventory save(Inventory inventory) {
        return inventoryRepository.save(inventory);
    }

    @Override
    public Optional<DarkStore> findDarkStoreById(String id) {
        return darkStoreRepository.findById(id);
    }

    @Override
    public String getSystemConfig(String key, String defaultValue) {
        return systemConfigurationRepository.findById(key)
                .map(SystemConfiguration::getConfigValue)
                .orElse(defaultValue);
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEvent savedEvent = outboxEventRepository.save(event);
        applicationEventPublisher.publishEvent(new ch.swissqcommerce.backend.domain.event.core.model.OutboxEventSavedEvent(savedEvent));
        return savedEvent;
    }


    @Override
    public Optional<Order> findById(Integer id) {
        return orderRepository.findById(id);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    @Override
    public void flush() {
        orderRepository.flush();
    }

    @Override
    public List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId) {
        return orderRepository.findByCustomerCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Override
    public HitlQueue save(HitlQueue queue) {
        return hitlQueueRepository.save(queue);
    }
}
