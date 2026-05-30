package ch.swissqcommerce.backend.domain.transaction.config;

import ch.swissqcommerce.backend.domain.transaction.core.service.OrderServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.core.service.LedgerServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.*;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionConfig {

    @Bean
    public LedgerUseCase ledgerUseCase(JournalEntryRepository journalEntryRepository,
                                       CustomerRepository customerRepository,
                                       RiderRepository riderRepository,
                                       WholesalerRepository wholesalerRepository,
                                       LedgerLineRepository ledgerLineRepository) {
        return new LedgerServiceImpl(
                journalEntryRepository,
                customerRepository,
                riderRepository,
                wholesalerRepository,
                ledgerLineRepository
        );
    }

    @Bean
    public OrderUseCase orderUseCase(OrderRepository orderRepository,
                                     CustomerPort customerPort,
                                     DarkStorePort darkStorePort,
                                     RiderPort riderPort,
                                     InventoryPort inventoryPort,
                                     SystemConfigPort systemConfigPort,
                                     LedgerUseCase ledgerUseCase,
                                     OutboxEventPort outboxEventPort,
                                     ApplicationEventPublisher eventPublisher) {
        return new OrderServiceImpl(
                orderRepository,
                customerPort,
                darkStorePort,
                riderPort,
                inventoryPort,
                systemConfigPort,
                ledgerUseCase,
                outboxEventPort,
                eventPublisher
        );
    }
}
