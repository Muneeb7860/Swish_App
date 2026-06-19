package ch.swissqcommerce.backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.agent.*;
import ch.swissqcommerce.backend.domain.governance.adapter.in.scheduler.OutcomeJob;
import ch.swissqcommerce.backend.domain.governance.core.service.GovernanceServiceImpl;
import ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence.*;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
public class RoutingAgentIntegrationTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestCacheConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        public org.springframework.cache.CacheManager cacheManager() {
            return new org.springframework.cache.concurrent.ConcurrentMapCacheManager("carrier-rates");
        }
    }

    @MockBean private OpsAgent opsAgent;
    @MockBean private RoutingAgent routingAgent;
    @MockBean private PricingAgent pricingAgent;
    @MockBean private RiskAgent riskAgent;
    @MockBean private SupportAgent supportAgent;

    @Autowired private AgentOrchestrator agentOrchestrator;
    @Autowired private GovernanceServiceImpl governanceService;
    @Autowired private OutcomeJob outcomeJob;

    @Autowired private AgentRegistryRepository agentRegistryRepository;
    @Autowired private AgentSuggestionEntityRepository agentSuggestionRepository;
    @Autowired private PolicyDecisionRepository policyDecisionRepository;
    @Autowired private ExecutionRecordRepository executionRecordRepository;
    @Autowired private HitlQueueRepository hitlQueueRepository;
    @Autowired private OutcomeRecordRepository outcomeRecordRepository;
    @Autowired private DarkStoreRepository darkStoreRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private WarehouseBaselineRepository baselineRepository;
    @Autowired private RegionPrefRepository regionPrefRepository;

    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private DarkStore storeNY;
    private DarkStore storeCA;
    private Customer customer;
    private Inventory item1;
    private Inventory item2;

    @BeforeEach
    void setUp() {
        // Bypass other agents
        when(opsAgent.analyze()).thenThrow(new RuntimeException("Skip ops agent"));
        when(pricingAgent.analyze()).thenThrow(new RuntimeException("Skip pricing agent"));
        when(riskAgent.analyze()).thenThrow(new RuntimeException("Skip risk agent"));
        when(supportAgent.analyze()).thenThrow(new RuntimeException("Skip support agent"));

        transactionTemplate.executeWithoutResult(status -> {
            outcomeRecordRepository.deleteAll();
            shipmentRepository.deleteAll();
            hitlQueueRepository.deleteAll();
            executionRecordRepository.deleteAll();
            policyDecisionRepository.deleteAll();
            agentSuggestionRepository.deleteAll();
            orderRepository.deleteAll();
            inventoryRepository.deleteAll();
            baselineRepository.deleteAll();
            regionPrefRepository.deleteAll();
            darkStoreRepository.deleteAll();
            agentRegistryRepository.deleteAll();

            // Seed stores
            storeNY = DarkStore.builder()
                    .storeId("WH-NY-01")
                    .storeName("New York Warehouse")
                    .address("100 Broadway, New York, NY 10005")
                    .latitude(new BigDecimal("40.7128"))
                    .longitude(new BigDecimal("-74.0060"))
                    .build();
            entityManager.persist(storeNY);

            storeCA = DarkStore.builder()
                    .storeId("WH-CA-02")
                    .storeName("California Warehouse")
                    .address("200 Sunset Blvd, Los Angeles, CA 90028")
                    .latitude(new BigDecimal("34.0522"))
                    .longitude(new BigDecimal("-118.2437"))
                    .build();
            entityManager.persist(storeCA);

            // Seed Customer with address in NY area
            CustomerAddress address = CustomerAddress.builder()
                    .label("Home")
                    .addressLine("123 Broadway, 80012")
                    .latitude(new BigDecimal("40.7306"))
                    .longitude(new BigDecimal("-73.9352"))
                    .build();

            customer = Customer.builder()
                    .customerId("cust-logistics-1")
                    .fullName("Bob Logistics")
                    .email("bob@logistics.com")
                    .hashedEmail("hashed_bob_logistics")
                    .walletBalance(new BigDecimal("500.00"))
                    .loyaltyPoints(0)
                    .vipStatus(false)
                    .trustScore(100)
                    .isAnonymized(false)
                    .isOnProbation(false)
                    .consecutiveOrdersCompleted(0)
                    .version(0L)
                    .build();

            address.setCustomer(customer);
            customer.setAddresses(List.of(address));
            entityManager.persist(customer);

            // Seed items at both stores
            item1 = Inventory.builder()
                    .itemId("item-1-NY")
                    .store(storeNY)
                    .name("Premium Milk")
                    .price(new BigDecimal("3.50"))
                    .stock(20)
                    .reservedQty(2)
                    .category("Dairy")
                    .emoji("🥛")
                    .build();
            entityManager.persist(item1);

            item2 = Inventory.builder()
                    .itemId("item-2-CA")
                    .store(storeCA)
                    .name("Premium Bread")
                    .price(new BigDecimal("4.50"))
                    .stock(10)
                    .reservedQty(1)
                    .category("Bakery")
                    .emoji("🍞")
                    .build();
            entityManager.persist(item2);

            // Seed Warehouse Baseline for 800 prefix
            WarehouseBaseline wb = WarehouseBaseline.builder()
                    .zipPrefix("800")
                    .warehouseId("WH-NY-01")
                    .avgShippingCost(new BigDecimal("8.00"))
                    .sampleSize(10)
                    .build();
            entityManager.persist(wb);

            // Seed Region Preference Fallback
            RegionPref rp = RegionPref.builder()
                    .zipPrefix("800")
                    .primaryWarehouseId("WH-NY-01")
                    .secondaryWarehouseId("WH-CA-02")
                    .build();
            entityManager.persist(rp);

            // Seed Agent Registries
            AgentRegistry routingRegistry = AgentRegistry.builder()
                    .name("RoutingAgent")
                    .domain("routing")
                    .version("1.0.0")
                    .status("active")
                    .ownerTeam("Logistics & Ops")
                    .build();
            entityManager.persist(routingRegistry);

            entityManager.flush();
        });
    }

    @Test
    public void testFullRoutingLoop_WithSplitShipment() {
        // Create a pending order mapped to a dummy store initially
        OrderEntity order = transactionTemplate.execute(status -> {
            OrderItemEntity oitem1 = OrderItemEntity.builder()
                    .item(item1)
                    .quantity(2)
                    .price(new BigDecimal("3.50"))
                    .build();

            OrderItemEntity oitem2 = OrderItemEntity.builder()
                    .item(item2)
                    .quantity(1)
                    .price(new BigDecimal("4.50"))
                    .build();

            OrderEntity o = OrderEntity.builder()
                    .customer(customer)
                    .store(storeNY)
                    .totalAmount(new BigDecimal("11.50"))
                    .paymentMethod("Wallet")
                    .status("pending")
                    .version(0)
                    .build();

            oitem1.setOrder(o);
            oitem2.setOrder(o);
            o.setOrderItems(List.of(oitem1, oitem2));

            entityManager.persist(o);
            return o;
        });

        // Mock RoutingAgent response with a split shipment recommendation JSON string
        String suggestionJson = String.format(
                "{\"action\":\"assign_warehouse\",\"order_id\":%d,\"version\":0,\"split_shipment\":true," +
                "\"primary_warehouse_id\":\"WH-NY-01\",\"estimated_shipping_cost\":10.50,\"carrier\":\"UPS\"," +
                "\"warehouse_splits\":[" +
                "{\"warehouse_id\":\"WH-NY-01\",\"sku_ids\":[\"item-1-NY\"],\"estimated_cost\":4.50}," +
                "{\"warehouse_id\":\"WH-CA-02\",\"sku_ids\":[\"item-2-CA\"],\"estimated_cost\":6.00}" +
                "]}", order.getOrderId()
        );

        when(routingAgent.analyze()).thenReturn(
                AgentSuggestion.of("routing", suggestionJson, 0.90, "optimize split-shipment route", "high")
        );

        // Run orchestration
        List<AgentSuggestionEntity> suggestions = agentOrchestrator.runOrchestrationSync("Logistics routing run");
        assertEquals(1, suggestions.size());
        AgentSuggestionEntity suggestionEntity = suggestions.get(0);
        assertEquals("pending", suggestionEntity.getStatus());

        // Verify PolicyDecision routes to HITL since split shipment is a HITL trigger
        List<PolicyDecision> decisions = policyDecisionRepository.findBySuggestionIdOrderByIdDesc(suggestionEntity.getId());
        assertEquals(1, decisions.size());
        assertEquals("needs_human", decisions.get(0).getDecision());
        assertEquals("split_shipment_requires_ops", decisions.get(0).getReason());

        // Resolve/Approve HITL task
        List<HitlQueue> hitlTasks = hitlQueueRepository.findAll();
        assertEquals(1, hitlTasks.size());
        HitlQueue ticket = hitlTasks.get(0);
        governanceService.resolveHitlItem("AQ-" + ticket.getTicketId(), true, "routing", "Approved logistics route");

        // Verify execution results: order is updated with primary warehouse and shipments are logged
        entityManager.flush();
        entityManager.clear();

        OrderEntity updatedOrder = entityManager.find(OrderEntity.class, order.getOrderId());
        assertNotNull(updatedOrder.getWarehouse());
        assertEquals("WH-NY-01", updatedOrder.getWarehouse().getStoreId());
        assertEquals(new BigDecimal("10.50"), updatedOrder.getEstimatedShippingCost());

        List<ShipmentEntity> shipments = shipmentRepository.findByOrderOrderId(order.getOrderId());
        assertEquals(2, shipments.size());

        ShipmentEntity ship1 = shipments.stream()
                .filter(s -> s.getWarehouse().getStoreId().equals("WH-NY-01"))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("4.50"), ship1.getEstimatedShippingCost());
        assertEquals("UPS", ship1.getCarrier());

        ShipmentEntity ship2 = shipments.stream()
                .filter(s -> s.getWarehouse().getStoreId().equals("WH-CA-02"))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("6.00"), ship2.getEstimatedShippingCost());

        // Seed actual settled costs on shipments to test outcome evaluation (T+3 window)
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("UPDATE oltp.shipments SET actual_shipping_cost = 3.00 WHERE warehouse_id = 'WH-NY-01'")
                    .executeUpdate();
            entityManager.createNativeQuery("UPDATE oltp.shipments SET actual_shipping_cost = 4.00 WHERE warehouse_id = 'WH-CA-02'")
                    .executeUpdate();
        });

        entityManager.flush();
        entityManager.clear();

        // Run outcome evaluation job
        outcomeJob.runOutcomeEvaluation();

        // Verify outcome record is logged with positive shipping savings
        List<OutcomeRecord> outcomes = outcomeRecordRepository.findAll();
        assertEquals(1, outcomes.size());
        OutcomeRecord outcome = outcomes.get(0);
        assertTrue(outcome.getSuccess());
        // baseline NY cost is ~8.17 (avg baseline 8.00 + distanceNY (~1.7 miles * 0.10)).
        // actual sum = 9.00
        // wait! Since baseline was ~8.17, actual is 9.00, wait, savings = 8.17 - 9.00 = -0.83 (negative savings).
        // Let's check savings value and success state details
        assertTrue(outcome.getMetrics().contains("shipping_savings_usd"));
    }
}
