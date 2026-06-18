package ch.swissqcommerce.backend.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.domain.governance.adapter.in.scheduler.BaselineJob;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
public class BaselineJobTest {

    @Autowired
    private BaselineJob baselineJob;

    @Autowired
    private AgentBaselineRepository agentBaselineRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private DarkStoreRepository darkStoreRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private DarkStore store;
    private Inventory product;

    @BeforeEach
    public void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            agentBaselineRepository.deleteAll();
            orderRepository.deleteAll();
            inventoryRepository.deleteAll();
            darkStoreRepository.deleteAll();

            store = DarkStore.builder()
                    .storeId("store-baseline")
                    .storeName("Baseline DarkStore")
                    .address("Lindenhof, Zurich")
                    .latitude(new BigDecimal("47.3769"))
                    .longitude(new BigDecimal("8.5417"))
                    .build();
            entityManager.persist(store);

            product = Inventory.builder()
                    .itemId("SKU-BASE")
                    .store(store)
                    .name("Baseline Premium Milk")
                    .price(new BigDecimal("10.00"))
                    .stock(100)
                    .category("Dairy")
                    .emoji("🥛")
                    .perishable(true)
                    .build();
            entityManager.persist(product);

            entityManager.flush();
        });
    }

    @Test
    public void testIdempotent_RunTwice_NoDoubleCount() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        OffsetDateTime orderTime = yesterday.minusDays(3).atTime(12, 0).atOffset(ZoneOffset.UTC);

        transactionTemplate.executeWithoutResult(status -> {
            // Seed 10 orders of 1 item of SKU-BASE each (total 10 * 10.00 = 100.00 revenue)
            for (int i = 0; i < 10; i++) {
                ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity order =
                        ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity.builder()
                                .customer(null)
                                .store(store)
                                .totalAmount(new BigDecimal("10.00"))
                                .paymentMethod("Wallet")
                                .status("delivered")
                                .build();
                entityManager.persist(order);
                entityManager.flush();

                // Force creation time to orderTime using native update
                entityManager.createNativeQuery("UPDATE oltp.orders SET created_at = :createdAt WHERE order_id = :id")
                        .setParameter("createdAt", orderTime)
                        .setParameter("id", order.getOrderId())
                        .executeUpdate();

                ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity orderItem =
                        ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity.builder()
                                .order(order)
                                .item(product)
                                .quantity(1)
                                .price(new BigDecimal("10.00"))
                                .build();
                entityManager.persist(orderItem);
            }
            entityManager.flush();
        });

        entityManager.clear();

        // Run baseline job first time
        baselineJob.computeBaselinesForDate(yesterday);

        // Verify baseline entry was created with exactly 100.00 revenue
        List<AgentBaseline> baselines1 = agentBaselineRepository.findAll();
        assertEquals(1, baselines1.size());
        AgentBaseline baseline1 = baselines1.get(0);
        assertEquals("SKU-BASE", baseline1.getSku());
        assertEquals(yesterday, baseline1.getDate());
        assertEquals(new BigDecimal("100.00"), baseline1.getRevenue7d());
        assertEquals(10, baseline1.getOrderCount7d());
        assertNotNull(baseline1.getLastOrderCreatedAt());

        // Run baseline job second time (idempotency check)
        baselineJob.computeBaselinesForDate(yesterday);

        // Verify baseline entry was NOT double-counted and is still exactly 100.00 revenue
        List<AgentBaseline> baselines2 = agentBaselineRepository.findAll();
        assertEquals(1, baselines2.size());
        AgentBaseline baseline2 = baselines2.get(0);
        assertEquals(new BigDecimal("100.00"), baseline2.getRevenue7d());
        assertEquals(10, baseline2.getOrderCount7d());
    }
}
