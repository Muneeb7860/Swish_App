package ch.swissqcommerce.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import ch.swissqcommerce.backend.domain.logistics.core.service.WarehouseSelectionService;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
public class RoutingCapacityIntegrationTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestCacheConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        public org.springframework.cache.CacheManager cacheManager() {
            return new org.springframework.cache.concurrent.ConcurrentMapCacheManager(
                    "carrier-rates");
        }
    }

    @Autowired private WarehouseSelectionService selectionService;
    @Autowired private DarkStoreRepository darkStoreRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;

    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private DarkStore storeNY;
    private DarkStore storeCA;
    private Customer customer;
    private Inventory itemNY;
    private Inventory itemCA;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(
                status -> {
                    orderRepository.deleteAll();
                    inventoryRepository.deleteAll();
                    darkStoreRepository.deleteAll();

                    // Seed store NY with capacity 1
                    storeNY =
                            DarkStore.builder()
                                    .storeId("WH-NY-01")
                                    .storeName("NY Warehouse")
                                    .address("100 Broadway, New York, NY 10005")
                                    .latitude(new BigDecimal("40.7128"))
                                    .longitude(new BigDecimal("-74.0060"))
                                    .dailyOrderCapacity(1)
                                    .build();
                    entityManager.persist(storeNY);

                    // Seed store CA with capacity 5
                    storeCA =
                            DarkStore.builder()
                                    .storeId("WH-CA-02")
                                    .storeName("CA Warehouse")
                                    .address("200 Sunset Blvd, Los Angeles, CA 90028")
                                    .latitude(new BigDecimal("34.0522"))
                                    .longitude(new BigDecimal("-118.2437"))
                                    .dailyOrderCapacity(5)
                                    .build();
                    entityManager.persist(storeCA);

                    customer =
                            Customer.builder()
                                    .customerId("cust-capacity-1")
                                    .fullName("Alice Capacity")
                                    .email("alice@capacity.com")
                                    .hashedEmail("hashed_alice_capacity")
                                    .walletBalance(new BigDecimal("200.00"))
                                    .loyaltyPoints(0)
                                    .vipStatus(false)
                                    .trustScore(100)
                                    .isAnonymized(false)
                                    .isOnProbation(false)
                                    .consecutiveOrdersCompleted(0)
                                    .version(0L)
                                    .build();

                    CustomerAddress address =
                            CustomerAddress.builder()
                                    .label("Home")
                                    .addressLine("123 Broadway, 80012")
                                    .latitude(new BigDecimal("40.7306"))
                                    .longitude(new BigDecimal("-73.9352"))
                                    .customer(customer)
                                    .build();
                    customer.setAddresses(List.of(address));
                    entityManager.persist(customer);

                    // Seed distinct item IDs to prevent Hibernate NonUniqueObjectException
                    itemNY =
                            Inventory.builder()
                                    .itemId("item-1-NY")
                                    .store(storeNY)
                                    .name("Organic Milk")
                                    .price(new BigDecimal("3.50"))
                                    .stock(10)
                                    .reservedQty(0)
                                    .category("Dairy")
                                    .emoji("🥛")
                                    .build();
                    entityManager.persist(itemNY);

                    itemCA =
                            Inventory.builder()
                                    .itemId("item-2-CA")
                                    .store(storeCA)
                                    .name("Organic Bread")
                                    .price(new BigDecimal("4.00"))
                                    .stock(10)
                                    .reservedQty(0)
                                    .category("Bakery")
                                    .emoji("🍞")
                                    .build();
                    entityManager.persist(itemCA);

                    entityManager.flush();
                });
    }

    @Test
    public void testOptimalWarehouseDisqualifiesExceededCapacity() {
        CustomerAddress addr = customer.getAddresses().get(0);
        RoutingOrderData orderData =
                new RoutingOrderData(
                        102, addr, null, List.of(new RoutingOrderData.OrderItem("item-1-NY", 1)));

        // 1. Initially, WH-NY-01 has daily capacity limit 1 and 0 orders today.
        // Fulfills the order successfully.
        Optional<WarehouseSelectionService.RoutingResult> resultOpt1 =
                selectionService.findOptimalWarehouse(orderData);
        assertTrue(resultOpt1.isPresent());
        assertEquals("WH-NY-01", resultOpt1.get().getPrimaryWarehouseId());

        // 2. Place 1 order assigned to WH-NY-01 today
        transactionTemplate.executeWithoutResult(
                status -> {
                    OrderEntity order1 =
                            OrderEntity.builder()
                                    .customer(customer)
                                    .store(storeNY)
                                    .warehouse(storeNY)
                                    .totalAmount(new BigDecimal("10.00"))
                                    .paymentMethod("Wallet")
                                    .status("pending")
                                    .version(0)
                                    .build();
                    entityManager.persist(order1);
                    entityManager.flush();
                    entityManager
                            .createNativeQuery(
                                    "UPDATE oltp.orders SET created_at = :createdAt WHERE order_id"
                                            + " = :id")
                            .setParameter("createdAt", OffsetDateTime.now(java.time.ZoneOffset.UTC))
                            .setParameter("id", order1.getOrderId())
                            .executeUpdate();
                });

        // 3. Query again. Since WH-NY-01 order count today = 1 (capacity limit = 1),
        // it must be disqualified.
        Optional<WarehouseSelectionService.RoutingResult> resultOpt2 =
                selectionService.findOptimalWarehouse(orderData);
        assertFalse(
                resultOpt2
                        .isPresent()); // Returns empty since the only stocked warehouse NY is over
        // capacity
    }
}
