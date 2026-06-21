package ch.swissqcommerce.backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.agent.*;
import ch.swissqcommerce.backend.gateway.ExecutionGateway;
import ch.swissqcommerce.backend.domain.governance.core.service.GovernanceServiceImpl;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
public class AgentControlPlaneIntegrationTest {

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

    @MockBean private OpsAgent opsAgent;
    @MockBean private RoutingAgent routingAgent;
    @MockBean private PricingAgent pricingAgent;
    @MockBean private RiskAgent riskAgent;
    @MockBean private SupportAgent supportAgent;

    @Autowired private AgentOrchestrator agentOrchestrator;
    @Autowired private GovernanceServiceImpl governanceService;
    @Autowired private ExecutionGateway executionGateway;

    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private DarkStoreRepository darkStoreRepository;
    @Autowired private AgentRegistryRepository agentRegistryRepository;
    @Autowired private AgentSuggestionEntityRepository agentSuggestionRepository;
    @Autowired private PolicyDecisionRepository policyDecisionRepository;
    @Autowired private ExecutionRecordRepository executionRecordRepository;
    @Autowired private HitlQueueRepository hitlQueueRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private DarkStore store;
    private Inventory product;

    @BeforeEach
    void setUp() {
        // Bypass other agents to speed up tests and isolate pricing agent execution
        when(opsAgent.analyze()).thenThrow(new RuntimeException("Skip ops agent"));
        when(routingAgent.analyze()).thenThrow(new RuntimeException("Skip routing agent"));
        when(riskAgent.analyze()).thenThrow(new RuntimeException("Skip risk agent"));
        when(supportAgent.analyze()).thenThrow(new RuntimeException("Skip support agent"));

        transactionTemplate.executeWithoutResult(status -> {
            // Clear tables in dependency order. Native deletes first for FK-child tables
            // (chargebacks -> order_items -> orders) that have no JPA repo here; otherwise the
            // inventory/dark_stores deleteAll below fails on leftover FKs from a prior test
            // (order-dependent pollution in the full @SpringBootTest suite).
            entityManager.createNativeQuery("DELETE FROM oltp.chargebacks").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM oltp.order_items").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM oltp.orders").executeUpdate();
            hitlQueueRepository.deleteAll();
            executionRecordRepository.deleteAll();
            policyDecisionRepository.deleteAll();
            agentSuggestionRepository.deleteAll();
            inventoryRepository.deleteAll();
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

            // Seed product item (SKU-12345)
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

            // Seed AgentRegistry for PricingAgent
            AgentRegistry pricingRegistry = AgentRegistry.builder()
                    .name("PricingAgent")
                    .domain("pricing")
                    .version("1.0.0")
                    .status("active")
                    .ownerTeam("Commercial")
                    .build();
            entityManager.persist(pricingRegistry);

            entityManager.flush();
        });
    }

    @Test
    public void testHappyPath_AutoApproveAndExecute() {
        // Given: pricing recommendation within thresholds (<5% change, low impact, confidence >= 0.8)
        // 10.00 -> 10.30 is a 3% change
        when(pricingAgent.analyze()).thenReturn(
                AgentSuggestion.of("pricing", "increase price of Swiss Milk Premium by 3.0%", 0.90, "high demand", "low")
        );

        // When
        List<AgentSuggestionEntity> suggestions = agentOrchestrator.runOrchestrationSync("Daily pricing check");

        // Then
        assertEquals(1, suggestions.size());
        AgentSuggestionEntity suggestion = suggestions.get(0);
        assertEquals("executed", suggestion.getStatus());

        // Verify Database pricing has been updated
        entityManager.clear();
        Inventory updatedProduct = inventoryRepository.findById("SKU-12345").orElseThrow();
        assertEquals(new BigDecimal("10.30"), updatedProduct.getPrice());

        // Verify ExecutionRecord was saved successfully
        List<ExecutionRecord> executionRecords = executionRecordRepository.findAll();
        assertEquals(1, executionRecords.size());
        assertTrue(executionRecords.get(0).getExecuted());
        assertEquals("AgentOrchestrator", executionRecords.get(0).getExecutedBy());

        // Verify PolicyDecision was written
        List<PolicyDecision> decisions = policyDecisionRepository.findBySuggestionIdOrderByIdDesc(suggestion.getId());
        assertEquals(1, decisions.size());
        assertEquals("approved", decisions.get(0).getDecision());
        assertEquals("auto_approve_low_impact", decisions.get(0).getReason());
    }

    @Test
    public void testEscalationToHitlAndHumanApproval() {
        // Given: high confidence but medium impact (needs human approval)
        when(pricingAgent.analyze()).thenReturn(
                AgentSuggestion.of("pricing", "increase price of Swiss Milk Premium by 3.0%", 0.90, "high demand", "medium")
        );

        // When: orchestrator runs
        List<AgentSuggestionEntity> suggestions = agentOrchestrator.runOrchestrationSync("Weekly pricing review");

        // Then: suggestion status is pending, and Hitl task is created
        assertEquals(1, suggestions.size());
        AgentSuggestionEntity suggestion = suggestions.get(0);
        assertEquals("pending", suggestion.getStatus());

        List<HitlQueue> hitlTasks = hitlQueueRepository.findAll();
        assertEquals(1, hitlTasks.size());
        HitlQueue ticket = hitlTasks.get(0);
        assertEquals("pending", ticket.getStatus());
        assertTrue(ticket.getDescription().contains("[PricingAgent]"));

        // When: Human supervisor approves the ticket
        governanceService.resolveHitlItem("AQ-" + ticket.getTicketId(), true, "pricing_manager", "Approved after review");

        // Then: suggestion status is executed, product price updated
        entityManager.clear();
        AgentSuggestionEntity suggestionAfterApproval = agentSuggestionRepository.findById(suggestion.getId()).orElseThrow();
        assertEquals("executed", suggestionAfterApproval.getStatus());

        Inventory updatedProduct = inventoryRepository.findById("SKU-12345").orElseThrow();
        assertEquals(new BigDecimal("10.30"), updatedProduct.getPrice());

        // Verify execution record
        List<ExecutionRecord> executionRecords = executionRecordRepository.findAll();
        assertEquals(1, executionRecords.size());
        assertTrue(executionRecords.get(0).getExecuted());
        assertEquals("pricing_manager", executionRecords.get(0).getExecutedBy());

        // Verify policy decisions
        List<PolicyDecision> decisions = policyDecisionRepository.findBySuggestionIdOrderByIdDesc(suggestion.getId());
        // Should have 2 decisions: initial engine (needs_human) + human override (approved)
        assertEquals(2, decisions.size());
        assertEquals("approved", decisions.get(0).getDecision());
        assertEquals("user:pricing_manager", decisions.get(0).getDecidedBy());
    }

    @Test
    public void testOptimisticLockStateDriftAbort() {
        // Given: pricing recommendation that goes to HITL (confidence 0.9, impact medium)
        when(pricingAgent.analyze()).thenReturn(
                AgentSuggestion.of("pricing", "increase price of Swiss Milk Premium by 3.0%", 0.90, "high demand", "medium")
        );

        List<AgentSuggestionEntity> suggestions = agentOrchestrator.runOrchestrationSync("Drift test");
        assertEquals(1, suggestions.size());
        AgentSuggestionEntity suggestion = suggestions.get(0);
        assertEquals("pending", suggestion.getStatus());

        HitlQueue ticket = hitlQueueRepository.findAll().get(0);

        // Simulate State Drift: change product price in the DB before human approves
        transactionTemplate.executeWithoutResult(status -> {
            Inventory item = inventoryRepository.findById("SKU-12345").orElseThrow();
            item.setPrice(new BigDecimal("11.50"));
            inventoryRepository.save(item);
        });

        // When: human supervisor attempts to approve, it should fail with OptimisticLockException
        assertThrows(RuntimeException.class, () -> {
            governanceService.resolveHitlItem("AQ-" + ticket.getTicketId(), true, "pricing_manager", "Approved");
        });

        // Then: suggestion status is failed, DB price is preserved at 11.50
        entityManager.clear();
        AgentSuggestionEntity suggestionAfter = agentSuggestionRepository.findById(suggestion.getId()).orElseThrow();
        assertEquals("failed", suggestionAfter.getStatus());

        Inventory productAfter = inventoryRepository.findById("SKU-12345").orElseThrow();
        assertEquals(new BigDecimal("11.50"), productAfter.getPrice());

        // Verify failed ExecutionRecord is written with error details
        List<ExecutionRecord> executionRecords = executionRecordRepository.findAll();
        assertEquals(1, executionRecords.size());
        assertFalse(executionRecords.get(0).getExecuted());
        assertTrue(executionRecords.get(0).getError().contains("STATE_DRIFT"));
    }

    @Test
    public void testPolicyReject_LowConfidence() {
        // Given: confidence is extremely low (0.50 < 0.60)
        when(pricingAgent.analyze()).thenReturn(
                AgentSuggestion.of("pricing", "increase price of Swiss Milk Premium by 3.0%", 0.50, "random guess", "low")
        );

        // When
        List<AgentSuggestionEntity> suggestions = agentOrchestrator.runOrchestrationSync("Low confidence test");

        // Then: suggestion is automatically rejected, and no HITL queue item is created
        assertEquals(1, suggestions.size());
        AgentSuggestionEntity suggestion = suggestions.get(0);
        assertEquals("rejected", suggestion.getStatus());

        List<HitlQueue> hitlTasks = hitlQueueRepository.findAll();
        assertEquals(0, hitlTasks.size());

        // Verify PolicyDecision is written as rejected
        List<PolicyDecision> decisions = policyDecisionRepository.findBySuggestionIdOrderByIdDesc(suggestion.getId());
        assertEquals(1, decisions.size());
        assertEquals("rejected", decisions.get(0).getDecision());
        assertEquals("low_confidence_reject", decisions.get(0).getReason());
    }
}
