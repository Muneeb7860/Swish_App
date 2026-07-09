package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final WholesalerRepository wholesalerRepository;
    private final DarkStoreRepository darkStoreRepository;

    @Override
    public void run(String... args) throws Exception {
        if (wholesalerRepository.count() == 0) {
            wholesalerRepository.save(
                    WholesalerEntity.builder()
                            .wholesalerId("WHOLESALER-1")
                            .name("Swiss Wholesale Distributors (B2B Core)")
                            .isPrimary(true)
                            .trustScore(95)
                            .isActive(true)
                            .academyDiscountActive(true)
                            .baseInvoiceAmount(BigDecimal.valueOf(25.00))
                            .fallbackInvoiceAmount(BigDecimal.valueOf(35.00))
                            .createdAt(OffsetDateTime.now())
                            .build());
            wholesalerRepository.save(
                    WholesalerEntity.builder()
                            .wholesalerId("wholesaler-2")
                            .name("Alpine Backups & Restock Co")
                            .isPrimary(false)
                            .trustScore(80)
                            .isActive(true)
                            .academyDiscountActive(false)
                            .baseInvoiceAmount(BigDecimal.valueOf(28.00))
                            .fallbackInvoiceAmount(BigDecimal.valueOf(38.00))
                            .createdAt(OffsetDateTime.now())
                            .build());
        }

        if (darkStoreRepository.count() == 0) {
            darkStoreRepository.save(
                    DarkStore.builder()
                            .storeId("store-test-1")
                            .storeName("Test Store")
                            .address("Test Address")
                            .latitude(BigDecimal.valueOf(47.3769))
                            .longitude(BigDecimal.valueOf(8.5417))
                            .storageCapacityLimit(5000)
                            .dailyOrderCapacity(500)
                            .active(true)
                            .build());
        }
    }
}
