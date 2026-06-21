package ch.swissqcommerce.backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.agent.*;
import ch.swissqcommerce.backend.domain.governance.adapter.in.scheduler.OutcomeJob;
import ch.swissqcommerce.backend.domain.governance.core.service.GovernanceServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
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
public class FraudAgentE2ETest {

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
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private DarkStore store;
    private Customer customer;

    @BeforeEach
    void setUp() {
        // Bypass other agents
        when(opsAgent.analyze()).thenThrow(new RuntimeException("Skip ops agent"));
        when(routingAgent.analyze()).thenThrow(new RuntimeException("Skip routing agent"));
        when(pricingAgent.analyze()).thenThrow(new RuntimeException("Skip pricing agent"));
        when(supportAgent.analyze()).thenThrow(new RuntimeException("Skip support agent"));

        transactionTemplate.executeWithoutResult(status -> {
            // Native deletes first for FK-child tables (chargebacks -> order_items -> orders ->
            // inventory) with no JPA repo cleared here; otherwise the dark_stores deleteAll below
            // fails on a leftover inventory FK from a prior test (order-dependent pollution in the
            // full @SpringBootTest suite).
            entityManager.createNativeQuery("DELETE FROM oltp.chargebacks").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM oltp.order_items").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM oltp.orders").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM oltp.inventory").executeUpdate();
            outcomeRecordRepository.deleteAll();
            hitlQueueRepository.deleteAll();
            executionRecordRepository.deleteAll();
            policyDecisionRepository.deleteAll();
            agentSuggestionRepository.deleteAll();
            darkStoreRepository.deleteAll();
            agentRegistryRepository.deleteAll();

            // Seed DarkStore
            store = DarkStore.builder()
                    .storeId("store-zuerich")
                    .storeName("Zurich DarkStore")
                    .address("Limmatquai 1, Zurich")
                    .latitude(new BigDecimal("47.3769"))
                    .longitude(new BigDecimal("8.5417"))
                    .build();
            entityManager.persist(store);

            // Seed Customer
            customer = Customer.builder()
                    .customerId("cust-e2e-1")
                    .fullName("Alice Risk")
                    .email("alice-risk@example.com")
                    .hashedEmail("hashed_alice_risk")
                    .walletBalance(new BigDecimal("500.00"))
                    .loyaltyPoints(0)
                    .vipStatus(false)
                    .trustScore(100)
                    .isAnonymized(false)
                    .isOnProbation(false)
                    .consecutiveOrdersCompleted(0)
                    .version(0L)
                    .build();
            entityManager.persist(customer);

            // Seed AgentRegistry for FraudAgent
            AgentRegistry registry = AgentRegistry.builder()
                    .name("FraudAgent")
                    .domain("risk")
                    .version("1.0.0")
                    .status("active")
                    .ownerTeam("Risk & Compliance")
                    .build();
            entityManager.persist(registry);

            // Seed AgentRegistry for RiskAgent (which Orchestrator looks up)
            AgentRegistry riskAgentRegistry = AgentRegistry.builder()
                    .name("RiskAgent")
                    .domain("risk")
                    .version("1.0.0")
                    .status("active")
                    .ownerTeam("Risk & Compliance")
                    .build();
            entityManager.persist(riskAgentRegistry);

            entityManager.flush();
        });
    }

    @Test
    public void testFullFraudLoop_WithAndWithoutChargeback() {
        // 1. Create two test orders in the DB (both pending initially)
        OrderEntity order1 = transactionTemplate.execute(status -> {
            OrderEntity o = OrderEntity.builder()
                    .customer(customer)
                    .store(store)
                    .totalAmount(new BigDecimal("150.00"))
                    .paymentMethod("Wallet")
                    .status("pending")
                    .version(0)
                    .build();
            entityManager.persist(o);
            return o;
        });

        OrderEntity order2 = transactionTemplate.execute(status -> {
            OrderEntity o = OrderEntity.builder()
                    .customer(customer)
                    .store(store)
                    .totalAmount(new BigDecimal("250.00"))
                    .paymentMethod("Wallet")
                    .status("pending")
                    .version(0)
                    .build();
            entityManager.persist(o);
            return o;
        });

        // --- PHASE 1: SUGGESTION & ROUTING FOR ORDER 1 ---
        // Suggest high-impact fraud hold on order 1
        when(riskAgent.analyze()).thenReturn(
                AgentSuggestion.of("risk", "hold_order order_id=" + order1.getOrderId() + " version=0", 0.95, "suspicious pattern", "high")
        );

        List<AgentSuggestionEntity> suggestions = agentOrchestrator.runOrchestrationSync("Fraud detection run 1");
        assertEquals(1, suggestions.size());
        AgentSuggestionEntity suggestion1 = suggestions.get(0);
        assertEquals("pending", suggestion1.getStatus());

        // Verify PolicyDecision routes to HITL with assignee role risk_analyst
        List<PolicyDecision> decisions1 = policyDecisionRepository.findBySuggestionIdOrderByIdDesc(suggestion1.getId());
        assertEquals(1, decisions1.size());
        assertEquals("needs_human", decisions1.get(0).getDecision());
        assertEquals("high_impact_requires_risk_analyst", decisions1.get(0).getReason());

        // Verify HITL task created in HitlQueue
        List<HitlQueue> hitlTasks1 = hitlQueueRepository.findAll();
        assertEquals(1, hitlTasks1.size());
        HitlQueue ticket1 = hitlTasks1.get(0);
        assertEquals("pending", ticket1.getStatus());
        assertTrue(ticket1.getDescription().contains("high_impact_requires_risk_analyst"));

        // Resolve/Approve HITL task
        governanceService.resolveHitlItem("AQ-" + ticket1.getTicketId(), true, "risk_analyst", "Approved fraud hold");

        // Verify order 1 status is now held
        entityManager.flush();
        entityManager.clear();
        OrderEntity order1Held = entityManager.find(OrderEntity.class, order1.getOrderId());
        assertEquals("held", order1Held.getStatus());
        assertEquals(1, order1Held.getVersion());

        // --- PHASE 2: SUGGESTION & ROUTING FOR ORDER 2 ---
        // Suggest high-impact fraud hold on order 2
        when(riskAgent.analyze()).thenReturn(
                AgentSuggestion.of("risk", "hold_order order_id=" + order2.getOrderId() + " version=0", 0.95, "suspicious pattern 2", "high")
        );

        List<AgentSuggestionEntity> suggestions2 = agentOrchestrator.runOrchestrationSync("Fraud detection run 2");
        assertEquals(1, suggestions2.size());
        AgentSuggestionEntity suggestion2 = suggestions2.stream()
                .filter(s -> s.getReason().contains("pattern 2"))
                .findFirst()
                .orElseThrow();
        assertEquals("pending", suggestion2.getStatus());

        // Get the new HITL task
        HitlQueue ticket2 = hitlQueueRepository.findAll().stream()
                .filter(t -> t.getDescription().contains("pattern 2"))
                .findFirst()
                .orElseThrow();

        // Resolve/Approve HITL task for order 2
        governanceService.resolveHitlItem("AQ-" + ticket2.getTicketId(), true, "risk_analyst", "Approved second fraud hold");

        // Verify order 2 status is now held
        entityManager.flush();
        entityManager.clear();
        OrderEntity order2Held = entityManager.find(OrderEntity.class, order2.getOrderId());
        assertEquals("held", order2Held.getStatus());
        assertEquals(1, order2Held.getVersion());

        // Seed a chargeback for order 2 (proving it was a bad hold or a missed loss)
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO oltp.chargebacks (order_id, amount, reason, filed_at, status)
                VALUES (:orderId, :amount, 'unauthorized', NOW(), 'disputed')
                """)
                .setParameter("orderId", order2.getOrderId())
                .setParameter("amount", new BigDecimal("250.00"))
                .executeUpdate();
        });

        entityManager.flush();
        entityManager.clear();

        // --- PHASE 3: OUTCOME JOB EVALUATION (T+30) ---
        outcomeJob.runOutcomeEvaluation();

        // Verify outcome records
        entityManager.clear();
        List<OutcomeRecord> outcomes = outcomeRecordRepository.findAll();
        assertEquals(2, outcomes.size());

        OutcomeRecord outcome1 = outcomes.stream()
                .filter(o -> o.getSuggestionId().equals(suggestion1.getId()))
                .findFirst()
                .orElseThrow();
        // Success since order 1 was held and no chargeback filed
        assertTrue(outcome1.getSuccess());
        assertTrue(outcome1.getMetrics().contains("\"prevented_chargeback_usd\":150.0"));

        OutcomeRecord outcome2 = outcomes.stream()
                .filter(o -> o.getSuggestionId().equals(suggestion2.getId()))
                .findFirst()
                .orElseThrow();
        // Failed since order 2 has a chargeback filed
        assertFalse(outcome2.getSuccess());
        assertTrue(outcome2.getMetrics().contains("\"prevented_chargeback_usd\":0.0"));
    }
}
