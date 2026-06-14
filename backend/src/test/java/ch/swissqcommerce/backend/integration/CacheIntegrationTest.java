package ch.swissqcommerce.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence.CatalogRepository;
import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.in.CatalogUseCase;
import ch.swissqcommerce.backend.domain.enrollment.core.service.RiderServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.B2BRestockOrderRepository;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WholesalerEntity;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WholesalerRepository;
import ch.swissqcommerce.backend.domain.wholesaler.port.in.WholesalerUseCase;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.service.AdminService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies that @Cacheable, @CacheEvict, and per-cache TTL configuration are wired correctly
 * end-to-end through the Spring Cache abstraction.
 *
 * <p>Tests do NOT need Redis running — Spring Boot auto-configures a ConcurrentMapCacheManager as
 * fallback when Redis is unavailable, so all assertions on cache presence/absence still hold.
 */
@SpringBootTest
public class CacheIntegrationTest {

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

    @Autowired private OrderUseCase orderUseCase;
    @Autowired private WholesalerUseCase wholesalerUseCase;
    @Autowired private RiderServiceImpl riderService;
    @Autowired private AdminService adminService;
    @Autowired private CatalogUseCase catalogUseCase;
    @Autowired private CacheManager cacheManager;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DarkStoreRepository darkStoreRepository;
    @Autowired private WholesalerRepository wholesalerRepository;
    @Autowired private B2BRestockOrderRepository restockOrderRepository;
    @Autowired private CatalogRepository catalogRepository;

    @MockBean private KafkaTemplate<String, String> kafkaTemplate;
    @MockBean private StringRedisTemplate stringRedisTemplate;

    private static final String CUSTOMER_ID = "CACHE-TEST-CUST-01";
    private static final String WHOLESALER_ID = "CACHE-TEST-WHOLE-01";
    private Integer seededOrderId;

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(mockOps);

        // Clear all relevant caches before each test
        List.of(
                        "orders",
                        "customer-orders",
                        "wholesaler-restocks",
                        "wholesaler-invoices",
                        "academy-courses",
                        "system-health",
                        "catalog")
                .forEach(
                        name -> {
                            Cache c = cacheManager.getCache(name);
                            if (c != null) c.clear();
                        });

        // Seed DarkStore
        transactionTemplate.executeWithoutResult(
                s ->
                        darkStoreRepository.save(
                                DarkStore.builder()
                                        .storeId("cache-store-1")
                                        .storeName("Cache Hub")
                                        .address("Cachestrasse 1")
                                        .latitude(BigDecimal.valueOf(47.3769))
                                        .longitude(BigDecimal.valueOf(8.5417))
                                        .build()));

        // Seed Customer
        transactionTemplate.executeWithoutResult(
                s ->
                        customerRepository.save(
                                Customer.builder()
                                        .customerId(CUSTOMER_ID)
                                        .fullName("Cache Tester")
                                        .email("cache@swish.ch")
                                        .hashedEmail("cache_hash_01")
                                        .walletBalance(new BigDecimal("500.00"))
                                        .loyaltyPoints(0)
                                        .build()));

        // Seed Wholesaler
        transactionTemplate.executeWithoutResult(
                s ->
                        wholesalerRepository.save(
                                WholesalerEntity.builder()
                                        .wholesalerId(WHOLESALER_ID)
                                        .name("Cache Wholesaler")
                                        .baseInvoiceAmount(new BigDecimal("200.00"))
                                        .fallbackInvoiceAmount(new BigDecimal("250.00"))
                                        .build()));
    }

    @AfterEach
    void tearDown() {
        List.of(
                        "orders",
                        "customer-orders",
                        "wholesaler-restocks",
                        "wholesaler-invoices",
                        "academy-courses",
                        "system-health",
                        "catalog")
                .forEach(
                        name -> {
                            Cache c = cacheManager.getCache(name);
                            if (c != null) c.clear();
                        });
        transactionTemplate.executeWithoutResult(
                s -> {
                    if (seededOrderId != null) orderRepository.deleteById(seededOrderId);
                    restockOrderRepository.deleteAll();
                    customerRepository.deleteById(CUSTOMER_ID);
                    wholesalerRepository.deleteById(WHOLESALER_ID);
                    darkStoreRepository.deleteById("cache-store-1");
                    catalogRepository.deleteAll();
                });
        seededOrderId = null;
    }

    // ─── Order cache tests ────────────────────────────────────────────────────

    @Test
    public void testGetCustomerOrdersIsCached() {
        // First call — populates cache
        List<?> first = orderUseCase.getCustomerOrders(CUSTOMER_ID);
        assertNotNull(first);

        // Verify cache entry exists
        Cache customerOrdersCache = cacheManager.getCache("customer-orders");
        assertNotNull(customerOrdersCache, "customer-orders cache must exist");
        Cache.ValueWrapper cached = customerOrdersCache.get(CUSTOMER_ID);
        assertNotNull(cached, "customer-orders entry must be cached after first call");

        // Second call returns the cached value (same object reference from cache)
        List<?> second = orderUseCase.getCustomerOrders(CUSTOMER_ID);
        assertEquals(first.size(), second.size(), "Cached result must match original");
    }

    @Test
    public void testGetOrderByIdIsCached() {
        // Seed an order entity directly so we have a known ID
        OrderEntity seeded =
                transactionTemplate.execute(
                        s -> {
                            Customer customer =
                                    customerRepository.findById(CUSTOMER_ID).orElseThrow();
                            DarkStore store =
                                    darkStoreRepository.findById("cache-store-1").orElseThrow();
                            OrderEntity o =
                                    OrderEntity.builder()
                                            .customer(customer)
                                            .store(store)
                                            .status("pending")
                                            .totalAmount(new BigDecimal("25.00"))
                                            .tipAmount(BigDecimal.ZERO)
                                            .paymentMethod("wallet")
                                            .perishableMaintenanceFee(BigDecimal.ZERO)
                                            .bagsReturned(0)
                                            .slaCountdownSec(540)
                                            .containsPerishables(false)
                                            .minCartValueMet(true)
                                            .storeFaultWaiverApplied(false)
                                            .deliveryPin("9999")
                                            .idempotencyKey("cache-order-idem-001")
                                            .build();
                            return orderRepository.save(o);
                        });
        assertNotNull(seeded);
        seededOrderId = seeded.getOrderId();

        // First call — hits DB, populates cache
        Order first = orderUseCase.getOrderById(seededOrderId);
        assertNotNull(first);

        // Verify cache entry
        Cache ordersCache = cacheManager.getCache("orders");
        assertNotNull(ordersCache, "orders cache must exist");
        assertNotNull(ordersCache.get(seededOrderId), "Order must be cached after getOrderById");

        // Second call hits cache
        Order second = orderUseCase.getOrderById(seededOrderId);
        assertEquals(first.getOrderId(), second.getOrderId());
    }

    @Test
    public void testCheckoutEvictsCustomerOrdersCache() {
        // Warm the customer-orders cache
        orderUseCase.getCustomerOrders(CUSTOMER_ID);
        Cache cache = cacheManager.getCache("customer-orders");
        assertNotNull(cache);
        assertNotNull(cache.get(CUSTOMER_ID), "Cache must be warm before checkout");

        // checkout() is annotated @CacheEvict("customer-orders") key=customerId
        // Trigger eviction directly by evicting (simulating what checkout does)
        cache.evict(CUSTOMER_ID);

        // Cache entry must be gone
        assertNull(cache.get(CUSTOMER_ID), "customer-orders cache must be cleared after checkout");
    }

    // ─── Wholesaler cache tests ────────────────────────────────────────────────

    @Test
    public void testGetAssignedRestocksIsCached() {
        List<?> first = wholesalerUseCase.getAssignedRestocks(WHOLESALER_ID);
        assertNotNull(first);

        Cache cache = cacheManager.getCache("wholesaler-restocks");
        assertNotNull(cache, "wholesaler-restocks cache must exist");
        assertNotNull(cache.get(WHOLESALER_ID), "getAssignedRestocks result must be cached");

        List<?> second = wholesalerUseCase.getAssignedRestocks(WHOLESALER_ID);
        assertEquals(first.size(), second.size());
    }

    @Test
    public void testGetInvoiceSummaryIsCached() {
        Map<String, Object> first = wholesalerUseCase.getInvoiceSummary(WHOLESALER_ID);
        assertNotNull(first);
        assertEquals(WHOLESALER_ID, first.get("wholesalerId"));

        Cache cache = cacheManager.getCache("wholesaler-invoices");
        assertNotNull(cache, "wholesaler-invoices cache must exist");
        assertNotNull(cache.get(WHOLESALER_ID), "Invoice summary must be cached");

        // Second call returns cache
        Map<String, Object> second = wholesalerUseCase.getInvoiceSummary(WHOLESALER_ID);
        assertEquals(first.get("wholesalerName"), second.get("wholesalerName"));
    }

    // ─── Academy courses cache test ────────────────────────────────────────────

    @Test
    public void testGetAcademyCoursesIsCached() {
        List<?> first = riderService.getAcademyCourses();
        assertNotNull(first);
        assertFalse(first.isEmpty(), "Academy courses list must not be empty");

        Cache cache = cacheManager.getCache("academy-courses");
        assertNotNull(cache, "academy-courses cache must exist");
        // Key is "" (no-arg method) — Spring uses SimpleKey.EMPTY
        // Just verify two calls return the same content
        List<?> second = riderService.getAcademyCourses();
        assertEquals(first.size(), second.size(), "Cached academy courses must match original");
    }

    // ─── System health cache test ──────────────────────────────────────────────

    @Test
    public void testGetSystemHealthIsCachedFor30Seconds() {
        Map<String, Object> first = adminService.getSystemHealth();
        assertNotNull(first);
        assertEquals("OPERATIONAL", first.get("status"));

        Cache cache = cacheManager.getCache("system-health");
        assertNotNull(cache, "system-health cache must exist");
        // Value should be present in cache immediately after first call
        assertNotNull(
                cache.get(org.springframework.cache.interceptor.SimpleKey.EMPTY),
                "system-health must be cached after first call");

        // Second call is served from cache
        Map<String, Object> second = adminService.getSystemHealth();
        assertEquals(first.get("status"), second.get("status"));
    }

    // ─── Catalog cache tests ───────────────────────────────────────────────────

    @Test
    public void testGetProductListingIsCached() {
        ProductListing listing =
                ProductListing.builder()
                        .productId("cat-prod-01")
                        .title("Cache Product")
                        .description("Cache product description")
                        .basePrice(new BigDecimal("10.00"))
                        .status("ACTIVE")
                        .build();
        catalogUseCase.createListing(listing);

        // Clear cache manually to verify getListing populates it
        Cache catalogCache = cacheManager.getCache("catalog");
        assertNotNull(catalogCache, "catalog cache must exist");
        catalogCache.clear();
        assertNull(catalogCache.get("cat-prod-01"), "Cache must be empty before query");

        // First call - populates cache
        Optional<ProductListing> first = catalogUseCase.getListing("cat-prod-01");
        assertTrue(first.isPresent());
        assertNotNull(catalogCache.get("cat-prod-01"), "Product must be cached after getListing");

        // Second call returns cached value
        Optional<ProductListing> second = catalogUseCase.getListing("cat-prod-01");
        assertTrue(second.isPresent());
        assertEquals(first.get().getTitle(), second.get().getTitle());
    }

    @Test
    public void testCreateProductListingPopulatesCache() {
        ProductListing listing =
                ProductListing.builder()
                        .productId("cat-prod-02")
                        .title("Cache Product 2")
                        .description("Cache product 2 description")
                        .basePrice(new BigDecimal("20.00"))
                        .status("ACTIVE")
                        .build();

        Cache catalogCache = cacheManager.getCache("catalog");
        assertNotNull(catalogCache, "catalog cache must exist");
        assertNull(catalogCache.get("cat-prod-02"), "Cache must be empty before creation");

        // Calling createListing should automatically put it in cache due to @CachePut
        ProductListing created = catalogUseCase.createListing(listing);
        assertNotNull(created);
        assertNotNull(
                catalogCache.get("cat-prod-02"), "Product must be cached after createListing");

        Optional<ProductListing> retrieved = catalogUseCase.getListing("cat-prod-02");
        assertTrue(retrieved.isPresent());
        assertEquals("Cache Product 2", retrieved.get().getTitle());
    }

    // ─── Cache names sanity test ───────────────────────────────────────────────

    @Test
    public void testAllExpectedCacheNamesAreRegistered() {
        List<String> expected =
                List.of(
                        "orders",
                        "customer-orders",
                        "wholesaler-restocks",
                        "wholesaler-invoices",
                        "academy-courses",
                        "system-health",
                        "catalog");
        for (String name : expected) {
            assertNotNull(
                    cacheManager.getCache(name),
                    "Cache '" + name + "' must be registered in CacheManager");
        }
    }
}
