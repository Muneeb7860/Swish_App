package ch.swissqcommerce.backend.domain.transaction.config;

import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import ch.swissqcommerce.backend.domain.transaction.core.service.LedgerServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.core.service.OrderServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.*;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionConfig {

    @Bean
    public LedgerUseCase ledgerUseCase(
            JournalEntryRepository journalEntryRepository,
            CustomerRepository customerRepository,
            EnrollmentOutPort enrollmentOutPort,
            WholesalerPort wholesalerPort,
            LedgerLineRepository ledgerLineRepository) {
        return new LedgerServiceImpl(
                journalEntryRepository,
                customerRepository,
                enrollmentOutPort,
                wholesalerPort,
                ledgerLineRepository);
    }

    @Bean
    public OrderUseCase orderUseCase(
            OrderPort orderPort,
            CustomerPort customerPort,
            DarkStorePort darkStorePort,
            RiderPort riderPort,
            InventoryPort inventoryPort,
            SystemConfigPort systemConfigPort,
            LedgerUseCase ledgerUseCase,
            OutboxEventPort outboxEventPort,
            ApplicationEventPublisher eventPublisher,
            HitlQueuePort hitlQueuePort) {
        return new OrderServiceImpl(
                orderPort,
                customerPort,
                darkStorePort,
                riderPort,
                inventoryPort,
                systemConfigPort,
                ledgerUseCase,
                outboxEventPort,
                eventPublisher,
                hitlQueuePort);
    }
}
