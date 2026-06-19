package ch.swissqcommerce.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.domain.payment.adapter.out.persistence.PaymentEntity;
import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestConfig.class)
public class PaymentIntegrationTest {

    @TestConfiguration
    static class TestCacheConfig {
        @Bean
        @Primary
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    "orders",
                    "customer-orders",
                    "wholesaler-restocks",
                    "wholesaler-invoices",
                    "academy-courses",
                    "system-health",
                    "catalog");
        }
    }

    @Autowired private OrderUseCase orderService;

    @Autowired private PaymentUseCase paymentUseCase;

    @Autowired private OrderRepository orderRepository;

    @Autowired private PaymentRepository paymentRepository;

    @Autowired private CustomerRepository customerRepository;

    @Autowired private DarkStoreRepository darkStoreRepository;

    @Autowired private InventoryRepository inventoryRepository;

    @Autowired private OutboxEventRepository outboxEventRepository;

    @Autowired private SystemConfigurationRepository systemConfigurationRepository;

    @Autowired private EntityManager entityManager;

    @Autowired private TransactionTemplate transactionTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean private StringRedisTemplate redisTemplate;
    @org.springframework.boot.test.mock.mockito.MockBean private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;


    private Customer customer;
    private DarkStore store;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(
                status -> {
                    orderRepository.deleteAll();
                    paymentRepository.deleteAll();
                    inventoryRepository.deleteAll();
                    darkStoreRepository.deleteAll();
                    customerRepository.deleteAll();
                    outboxEventRepository.deleteAll();
                    systemConfigurationRepository.deleteAll();

                    store =
                            DarkStore.builder()
                                    .storeId("Central Store")
                                    .storeName("Central Store")
                                    .address("10 Bahnhofstrasse, Zurich")
                                    .latitude(new BigDecimal("47.376900"))
                                    .longitude(new BigDecimal("8.541700"))
                                    .build();
                    entityManager.persist(store);

                    customer =
                            Customer.builder()
                                    .customerId("CUST-200")
                                    .fullName("Kathryn Janeway")
                                    .email("janeway@starfleet.org")
                                    .hashedEmail("janeway_hash_321")
                                    .walletBalance(new BigDecimal("1000.00"))
                                    .loyaltyPoints(0)
                                    .trustScore(100)
                                    .build();
                    entityManager.persist(customer);

                    inventory =
                            Inventory.builder()
                                    .itemId("BREAD-001")
                                    .store(store)
                                    .name("Sourdough Bread")
                                    .price(new BigDecimal("5.00"))
                                    .stock(10)
                                    .category("Bakery")
                                    .emoji("🥖")
                                    .perishable(false)
                                    .build();
                    entityManager.persist(inventory);

                    SystemConfiguration config =
                            SystemConfiguration.builder()
                                    .configKey("current_weather")
                                    .configValue("Cloudy")
                                    .build();
                    entityManager.persist(config);
                    entityManager.flush();
                });

        // Stub Redis cache operations
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.when(valueOps.get("wallet:balance:CUST-200")).thenReturn("1000.00");
    }

    @Test
    public void testAuthorizeAndCapturePaymentFlow() {
        List<OrderUseCase.CartItem> items = List.of(new OrderUseCase.CartItem("BREAD-001", 2));
        String orderIdempotencyKey = UUID.randomUUID().toString();

        Order order =
                orderService.checkout(
                        customer.getCustomerId(),
                        items,
                        "wallet",
                        BigDecimal.ZERO,
                        0,
                        orderIdempotencyKey);

        assertNotNull(order);
        assertEquals("pending", order.getStatus());
        assertTrue(order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0);

        String paymentIdempotencyKey = UUID.randomUUID().toString();
        Payment authorization =
                paymentUseCase.authorizePayment(
                        order.getOrderId(),
                        customer.getCustomerId(),
                        order.getTotalAmount(),
                        "Wallet",
                        paymentIdempotencyKey);

        assertNotNull(authorization);
        assertNotNull(authorization.getPaymentId());
        assertEquals("AUTHORIZED", authorization.getStatus());
        assertEquals(order.getTotalAmount(), authorization.getAmount());
        assertEquals(paymentIdempotencyKey, authorization.getIdempotencyKey());

        PaymentEntity persistedPayment =
                paymentRepository
                        .findById(authorization.getPaymentId())
                        .orElseThrow(
                                () -> new NoSuchElementException("Authorized payment not found"));
        assertEquals("AUTHORIZED", persistedPayment.getStatus());

        List<OutboxEvent> eventsAfterAuthorize = outboxEventRepository.findAll();
        assertTrue(
                eventsAfterAuthorize.stream()
                        .anyMatch(e -> "payment.authorized".equals(e.getEventType())));
        assertTrue(
                eventsAfterAuthorize.stream()
                        .anyMatch(e -> "payment.fraud_check".equals(e.getEventType())));

        Payment capturedPayment = paymentUseCase.capturePayment(authorization.getPaymentId());
        assertEquals("CAPTURED", capturedPayment.getStatus());
        assertNotNull(capturedPayment.getCapturedAt());

        PaymentEntity persistedCapturedPayment =
                paymentRepository
                        .findById(capturedPayment.getPaymentId())
                        .orElseThrow(
                                () -> new NoSuchElementException("Captured payment not found"));
        assertEquals("CAPTURED", persistedCapturedPayment.getStatus());
        assertNotNull(persistedCapturedPayment.getCapturedAt());

        List<OutboxEvent> eventsAfterCapture = outboxEventRepository.findAll();
        assertTrue(
                eventsAfterCapture.stream()
                        .anyMatch(e -> "payment.captured".equals(e.getEventType())));
        assertTrue(
                eventsAfterCapture.stream()
                        .anyMatch(e -> "payment.notification".equals(e.getEventType())));
    }

    @Test
    public void testAuthorizePaymentIsIdempotent() {
        List<OrderUseCase.CartItem> items = List.of(new OrderUseCase.CartItem("BREAD-001", 1));
        String orderIdempotencyKey = UUID.randomUUID().toString();

        Order order =
                orderService.checkout(
                        customer.getCustomerId(),
                        items,
                        "wallet",
                        BigDecimal.ZERO,
                        0,
                        orderIdempotencyKey);

        String paymentIdempotencyKey = "PIDEMP-123";
        Payment firstPayment =
                paymentUseCase.authorizePayment(
                        order.getOrderId(),
                        customer.getCustomerId(),
                        order.getTotalAmount(),
                        "Wallet",
                        paymentIdempotencyKey);

        Payment secondPayment =
                paymentUseCase.authorizePayment(
                        order.getOrderId(),
                        customer.getCustomerId(),
                        order.getTotalAmount(),
                        "Wallet",
                        paymentIdempotencyKey);

        assertEquals(firstPayment.getPaymentId(), secondPayment.getPaymentId());
        assertEquals("AUTHORIZED", secondPayment.getStatus());
        assertEquals(1, paymentRepository.findAll().size());
    }
}
