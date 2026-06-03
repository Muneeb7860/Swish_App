package ch.swissqcommerce.backend.integration;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class OrderIntegrationTest {

    @Autowired
    private OrderUseCase orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DarkStoreRepository darkStoreRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private SystemConfigurationRepository systemConfigurationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Customer customer;
    private DarkStore store;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            // Clear repositories to ensure test isolation
            paymentRepository.deleteAll();
            orderRepository.deleteAll();
            inventoryRepository.deleteAll();
            darkStoreRepository.deleteAll();
            customerRepository.deleteAll();
            outboxEventRepository.deleteAll();
            systemConfigurationRepository.deleteAll();

            // 1. Seed DarkStore
            store = DarkStore.builder()
                    .storeId("Central Store")
                    .storeName("Central Store")
                    .address("10 Bahnhofstrasse, Zurich")
                    .latitude(new BigDecimal("47.376900"))
                    .longitude(new BigDecimal("8.541700"))
                    .build();
            entityManager.persist(store);

            // 2. Seed Customer with sufficient wallet balance
            customer = Customer.builder()
                    .customerId("CUST-100")
                    .fullName("Jean-Luc Picard")
                    .email("picard@starfleet.org")
                    .hashedEmail("picard_hash_123")
                    .walletBalance(new BigDecimal("500.00"))
                    .loyaltyPoints(0)
                    .trustScore(100)
                    .build();
            entityManager.persist(customer);

            // 3. Seed Inventory item with stock
            inventory = Inventory.builder()
                    .itemId("MILK-001")
                    .store(store)
                    .name("Organic Milk 1L")
                    .price(new BigDecimal("2.50"))
                    .stock(10)
                    .category("Dairy")
                    .emoji("🥛")
                    .perishable(true)
                    .build();
            entityManager.persist(inventory);

            // Ensure Sunny weather is configured for SLA calculations
            SystemConfiguration config = SystemConfiguration.builder()
                    .configKey("current_weather")
                    .configValue("Sunny")
                    .build();
            entityManager.persist(config);
            
            entityManager.flush();
        });
    }

    @Test
    @Transactional
    public void testEndToEndOrderFlow() {
        // Given
        List<OrderUseCase.CartItem> items = List.of(
                new OrderUseCase.CartItem("MILK-001", 3) // Buy 3 milks
        );
        String idempotencyKey = UUID.randomUUID().toString();

        // When
        Order order = orderService.checkout(
                customer.getCustomerId(),
                items,
                "wallet",
                BigDecimal.ZERO,
                0,
                idempotencyKey
        );

        // Then: Verify order object
        assertNotNull(order);
        assertNotNull(order.getOrderId());
        assertEquals("pending", order.getStatus());
        assertEquals(new BigDecimal("7.50"), order.getTotalAmount()); // 3 * 2.50 = 7.50

        // Then: Verify database persistence
        Order persistedOrder = orderRepository.findById(order.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found in database"));
        assertEquals("pending", persistedOrder.getStatus());
        assertEquals(new BigDecimal("7.50"), persistedOrder.getTotalAmount());

        // Then: Verify stock was decremented properly
        Inventory updatedInventory = inventoryRepository.findById("MILK-001").orElseThrow();
        assertEquals(7, updatedInventory.getStock()); // 10 - 3 = 7

        // Then: Verify outbox event was generated for event-driven consistency
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertFalse(outboxEvents.isEmpty());
        assertTrue(outboxEvents.stream().anyMatch(e -> "order.placed".equals(e.getEventType())));
    }

    @Test
    @Transactional
    public void testOrderStateTransitions() {
        // Given: Create a pending order
        List<OrderUseCase.CartItem> items = List.of(
                new OrderUseCase.CartItem("MILK-001", 1)
        );
        Order order = orderService.checkout(
                customer.getCustomerId(),
                items,
                "wallet",
                BigDecimal.ZERO,
                0,
                UUID.randomUUID().toString()
        );

        assertEquals("pending", order.getStatus());

        // When: transition state
        order.setStatus("confirmed");
        orderRepository.save(order);

        // Then: Verify transition persisted
        Order updatedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertEquals("confirmed", updatedOrder.getStatus());
    }

    @Test
    public void testConcurrentOrderCheckoutStress() throws InterruptedException {
        // Given: Set stock to exactly 1 item
        inventory.setStock(1);
        inventoryRepository.save(inventory);

        int concurrentThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentThreads; i++) {
            futures.add(executor.submit(() -> {
                latch.await(); // Synchronize start
                try {
                    // Try to checkout the last milk item concurrently
                    orderService.checkout(
                            customer.getCustomerId(),
                            List.of(new OrderUseCase.CartItem("MILK-001", 1)),
                            "wallet",
                            BigDecimal.ZERO,
                            0,
                            UUID.randomUUID().toString() // Unique idempotency key per request
                    );
                    successCounter.incrementAndGet();
                } catch (Exception e) {
                    failureCounter.incrementAndGet();
                }
                return null;
            }));
        }

        // When: Trigger concurrent checkout attempts
        latch.countDown();

        // Wait for all threads to complete
        for (Future<Void> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                // Task failed
            } catch (TimeoutException e) {
                fail("Concurrent execution timed out");
            }
        }

        executor.shutdown();

        // Then: Exactly 1 checkout must succeed, and remaining must fail due to stock depletion
        assertEquals(1, successCounter.get(), "Exactly one checkout attempt must succeed");
        assertEquals(concurrentThreads - 1, failureCounter.get(), "All other concurrent attempts must be blocked");

        // Then: Verify stock is exactly 0
        Inventory updatedInventory = inventoryRepository.findById("MILK-001").orElseThrow();
        assertEquals(0, updatedInventory.getStock());
    }
}
