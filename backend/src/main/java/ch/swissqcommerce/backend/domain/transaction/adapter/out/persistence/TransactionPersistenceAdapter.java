package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.transaction.port.out.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TransactionPersistenceAdapter implements 
        CustomerPort, RiderPort, InventoryPort, DarkStorePort, SystemConfigPort, OutboxEventPort {

    private final CustomerRepository customerRepository;
    private final RiderRepository riderRepository;
    private final InventoryRepository inventoryRepository;
    private final DarkStoreRepository darkStoreRepository;
    private final SystemConfigurationRepository systemConfigurationRepository;
    private final OutboxEventRepository outboxEventRepository;

    public TransactionPersistenceAdapter(CustomerRepository customerRepository,
                                         RiderRepository riderRepository,
                                         InventoryRepository inventoryRepository,
                                         DarkStoreRepository darkStoreRepository,
                                         SystemConfigurationRepository systemConfigurationRepository,
                                         OutboxEventRepository outboxEventRepository) {
        this.customerRepository = customerRepository;
        this.riderRepository = riderRepository;
        this.inventoryRepository = inventoryRepository;
        this.darkStoreRepository = darkStoreRepository;
        this.systemConfigurationRepository = systemConfigurationRepository;
        this.outboxEventRepository = outboxEventRepository;
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
        return outboxEventRepository.save(event);
    }
}
