package ch.swissqcommerce.backend.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.domain.governance.adapter.in.scheduler.OutcomeJob;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
public class OutcomeJobTest {

    @Autowired
    private OutcomeJob outcomeJob;

    @Autowired
    private OutcomeRecordRepository outcomeRecordRepository;

    @Autowired
    private ExecutionRecordRepository executionRecordRepository;

    @Autowired
    private AgentSuggestionEntityRepository agentSuggestionRepository;

    @Autowired
    private AgentRegistryRepository agentRegistryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private DarkStoreRepository darkStoreRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private DarkStore store;
    private Inventory product;
    private AgentRegistry agentRegistry;

    @BeforeEach
    public void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            outcomeRecordRepository.deleteAll();
            executionRecordRepository.deleteAll();
            agentSuggestionRepository.deleteAll();
            orderRepository.deleteAll();
            inventoryRepository.deleteAll();
            darkStoreRepository.deleteAll();
            agentRegistryRepository.deleteAll();

            store = DarkStore.builder()
                    .storeId("store-zuerich")
                    .storeName("Zurich DarkStore")
                    .address("Limmatquai 1, Zurich")
                    .latitude(new BigDecimal("47.3769"))
                    .longitude(new BigDecimal("8.5417"))
                    .build();
            entityManager.persist(store);

            product = Inventory.builder()
                    .itemId("SKU-12345")
                    .store(store)
                    .name("Swiss Milk Premium")
                    .price(new BigDecimal("10.00"))
                    .stock(10)
                    .category("Dairy")
                    .emoji("🥛")
                    .perishable(true)
                    .build();
            entityManager.persist(product);

            agentRegistry = AgentRegistry.builder()
                    .name("PricingAgent")
                    .domain("pricing")
                    .version("1.0.0")
                    .status("active")
                    .ownerTeam("Commercial")
                    .build();
            entityManager.persist(agentRegistry);

            entityManager.flush();
        });
    }

    @Test
    public void testOutcomeEvaluation_Success() {
        transactionTemplate.executeWithoutResult(status -> {
            // 1. Create a suggestion executed now (T = now)
            UUID suggestionId = UUID.randomUUID();
            AgentSuggestionEntity suggestion = AgentSuggestionEntity.builder()
                    .id(suggestionId)
                    .traceId(UUID.randomUUID())
                    .agent(agentRegistry)
                    .domain("pricing")
                    .entityId("SKU-12345")
                    .recommendation("{\"action\":\"update_price\",\"old_value\":1250.00,\"new_value\":1392.50}")
                    .confidence(new BigDecimal("0.90"))
                    .reason("High demand")
                    .impact("low")
                    .status("executed")
                    .expiresAt(OffsetDateTime.now().plusHours(1))
                    .build();
            entityManager.persist(suggestion);

            PolicyDecision decision = PolicyDecision.builder()
                    .suggestion(suggestion)
                    .decision("approved")
                    .policyVersion("v1")
                    .reason("Within thresholds")
                    .decidedBy("policy_engine_v1")
                    .build();
            entityManager.persist(decision);

            ExecutionRecord execRecord = ExecutionRecord.builder()
                    .suggestion(suggestion)
                    .decision(decision)
                    .executed(true)
                    .executedBy("AgentOrchestrator")
                    .build();
            entityManager.persist(execRecord);

            // 2. Create delivered order in the T+1 to T+7 window (T is now, so start = now+24h, end = now+7d)
            // Seeding order 3 days in the future (OffsetDateTime.now().plusDays(3))
            ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity order =
                    ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity.builder()
                            .customer(null)
                            .store(store)
                            .totalAmount(new BigDecimal("1392.50"))
                            .paymentMethod("Wallet")
                            .status("delivered")
                            .build();
            entityManager.persist(order);

            entityManager.flush();
            entityManager.createNativeQuery("UPDATE oltp.orders SET created_at = :createdAt WHERE order_id = :id")
                    .setParameter("createdAt", OffsetDateTime.now().plusDays(3))
                    .setParameter("id", order.getOrderId())
                    .executeUpdate();

            // Order items: quantity 10, price 139.25 (total 1392.50)
            ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity orderItem =
                    ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity.builder()
                            .order(order)
                            .item(product)
                            .quantity(10)
                            .price(new BigDecimal("139.25"))
                            .build();
            entityManager.persist(orderItem);

            entityManager.flush();
        });

        // Clear Persistence Context
        entityManager.clear();

        // Run evaluation
        outcomeJob.runOutcomeEvaluation();

        // Verify OutcomeRecord exists and success is true (1392.50 revenue vs 1250.00 baseline)
        List<OutcomeRecord> outcomes = outcomeRecordRepository.findAll();
        assertEquals(1, outcomes.size());
        OutcomeRecord outcome = outcomes.get(0);
        assertTrue(outcome.getSuccess());
    }

    @Test
    public void testOutcomeEvaluation_DegradedAlert() {
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) {
                UUID suggestionId = UUID.randomUUID();
                AgentSuggestionEntity suggestion = AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .traceId(UUID.randomUUID())
                        .agent(agentRegistry)
                        .domain("pricing")
                        .entityId("SKU-12345")
                        .recommendation("{\"action\":\"update_price\",\"old_value\":1250.00,\"new_value\":1392.50}")
                        .confidence(new BigDecimal("0.90"))
                        .reason("High demand")
                        .impact("low")
                        .status("executed")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();
                entityManager.persist(suggestion);

                PolicyDecision decision = PolicyDecision.builder()
                        .suggestion(suggestion)
                        .decision("approved")
                        .policyVersion("v1")
                        .reason("Within thresholds")
                        .decidedBy("policy_engine_v1")
                        .build();
                entityManager.persist(decision);

                ExecutionRecord execRecord = ExecutionRecord.builder()
                        .suggestion(suggestion)
                        .decision(decision)
                        .executed(true)
                        .executedBy("AgentOrchestrator")
                        .build();
                entityManager.persist(execRecord);
            }
            entityManager.flush();
        });

        // Clear Persistence Context
        entityManager.clear();

        // Run evaluation: all 3 should be evaluated as failed (actual revenue 0.0 < 1250.00 baseline)
        outcomeJob.runOutcomeEvaluation();

        List<OutcomeRecord> outcomes = outcomeRecordRepository.findAll();
        assertEquals(3, outcomes.size());
    }

    @Test
    public void testOutcomeEvaluation_PreCalculatedBaseline() {
        OffsetDateTime executionTime = OffsetDateTime.now();
        LocalDate yesterday = executionTime.toLocalDate().minusDays(1);

        transactionTemplate.executeWithoutResult(status -> {
            // Seed a dynamic inventory SKU
            Inventory dynamicProduct = Inventory.builder()
                    .itemId("SKU-DYNAMIC")
                    .store(store)
                    .name("Dynamic Milk")
                    .price(new BigDecimal("12.00"))
                    .stock(50)
                    .category("Dairy")
                    .emoji("🥛")
                    .perishable(true)
                    .build();
            entityManager.persist(dynamicProduct);

            // Seed a pre-calculated baseline of 100.00 for SKU-DYNAMIC
            AgentBaseline baseline = AgentBaseline.builder()
                    .sku("SKU-DYNAMIC")
                    .date(yesterday)
                    .revenue7d(new BigDecimal("100.00"))
                    .marginPct(new BigDecimal("0.20"))
                    .orderCount7d(5)
                    .lastOrderCreatedAt(executionTime.minusDays(2))
                    .build();
            entityManager.persist(baseline);

            // Create suggestion & execution record
            UUID suggestionId = UUID.randomUUID();
            AgentSuggestionEntity suggestion = AgentSuggestionEntity.builder()
                    .id(suggestionId)
                    .traceId(UUID.randomUUID())
                    .agent(agentRegistry)
                    .domain("pricing")
                    .entityId("SKU-DYNAMIC")
                    .recommendation("{\"action\":\"update_price\",\"old_value\":10.00,\"new_value\":12.00}")
                    .confidence(new BigDecimal("0.95"))
                    .reason("High demand dynamic")
                    .impact("low")
                    .status("executed")
                    .expiresAt(OffsetDateTime.now().plusHours(1))
                    .build();
            entityManager.persist(suggestion);

            PolicyDecision decision = PolicyDecision.builder()
                    .suggestion(suggestion)
                    .decision("approved")
                    .policyVersion("v1")
                    .reason("Within thresholds")
                    .decidedBy("policy_engine_v1")
                    .build();
            entityManager.persist(decision);

            ExecutionRecord execRecord = ExecutionRecord.builder()
                    .suggestion(suggestion)
                    .decision(decision)
                    .executed(true)
                    .executedBy("AgentOrchestrator")
                    .build();
            entityManager.persist(execRecord);

            // Seed delivered order item in the future window (T+3d)
            ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity order =
                    ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity.builder()
                            .customer(null)
                            .store(store)
                            .totalAmount(new BigDecimal("150.00"))
                            .paymentMethod("Wallet")
                            .status("delivered")
                            .build();
            entityManager.persist(order);
            entityManager.flush();

            entityManager.createNativeQuery("UPDATE oltp.orders SET created_at = :createdAt WHERE order_id = :id")
                    .setParameter("createdAt", executionTime.plusDays(3))
                    .setParameter("id", order.getOrderId())
                    .executeUpdate();

            ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity orderItem =
                    ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity.builder()
                            .order(order)
                            .item(dynamicProduct)
                            .quantity(10)
                            .price(new BigDecimal("15.00")) // total 150.00 actual revenue
                            .build();
            entityManager.persist(orderItem);

            entityManager.flush();
        });

        // Clear Persistence Context
        entityManager.clear();

        // Run evaluation: actual revenue is 150.00, baseline is 100.00 (from agent_baseline table), delta = 50.00 > 0 (success)
        outcomeJob.runOutcomeEvaluation();

        List<OutcomeRecord> outcomes = outcomeRecordRepository.findAll();
        assertFalse(outcomes.isEmpty());
        OutcomeRecord outcome = outcomes.stream()
                .filter(o -> "SKU-DYNAMIC".equals(o.getSuggestion().getEntityId()))
                .findFirst()
                .orElseThrow();

        assertTrue(outcome.getSuccess());
        assertTrue(outcome.getMetrics().contains("50.0")); // revenue_delta = 50.0
    }
}
