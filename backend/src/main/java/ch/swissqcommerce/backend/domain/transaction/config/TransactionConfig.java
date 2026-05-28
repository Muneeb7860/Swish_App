package ch.swissqcommerce.backend.domain.transaction.config;

import ch.swissqcommerce.backend.domain.transaction.core.service.OrderServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionConfig {

    @Bean
    public OrderUseCase orderUseCase(OrderRepository orderRepository,
                                     CustomerRepository customerRepository,
                                     DarkStoreRepository darkStoreRepository,
                                     ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository riderRepository,
                                     InventoryRepository inventoryRepository,
                                     SystemConfigurationRepository systemConfigurationRepository,
                                     LedgerUseCase ledgerUseCase,
                                     OutboxEventRepository outboxRepository,
                                     ApplicationEventPublisher eventPublisher) {
        return new OrderServiceImpl(orderRepository, customerRepository, darkStoreRepository, riderRepository,
                inventoryRepository, systemConfigurationRepository, ledgerUseCase, outboxRepository, eventPublisher);
    }
}
