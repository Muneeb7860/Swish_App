package ch.swissqcommerce.backend.gateway;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence.ShipmentRepository;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.autoconfigure.exclude="
})
public class ExecutionGatewayIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("swiss_db")
            .withUsername("sa")
            .withPassword("");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private InventoryRepository inventoryRepo;

    @Autowired
    private AgentSuggestionEntityRepository agentSuggestionRepo;

    @Autowired
    private PolicyDecisionRepository policyDecisionRepo;

    @Autowired
    private ExecutionRecordRepository executionRecordRepo;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private ShipmentRepository shipmentRepo;

    @Autowired
    private DarkStoreRepository darkStoreRepo;

    private ExecutionGateway executionGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private DarkStore store;
    private AgentRegistry riskAgent;

    @BeforeEach
    public void setUp() {
        executionGateway = new ExecutionGateway(
                inventoryRepo,
                objectMapper,
                agentSuggestionRepo,
                policyDecisionRepo,
                executionRecordRepo,
                entityManager,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                orderRepo,
                shipmentRepo,
                darkStoreRepo
        );

        executionRecordRepo.deleteAll();
        policyDecisionRepo.deleteAll();
        agentSuggestionRepo.deleteAll();

        // Seed DarkStore
        store = DarkStore.builder()
                .storeId("store-zuerich-int")
                .storeName("Zurich DarkStore Int")
                .address("Limmatquai 1, Zurich")
                .latitude(new BigDecimal("47.3769"))
                .longitude(new BigDecimal("8.5417"))
                .build();
        entityManager.persist(store);

        // Seed Agent Registry
        riskAgent = AgentRegistry.builder()
                .name("FraudAgent")
                .domain("risk")
                .version("1.0.0")
                .status("active")
                .ownerTeam("Risk & Compliance")
                .build();
        entityManager.persist(riskAgent);

        entityManager.flush();
    }

    @Test
    public void testExecute_HoldOrder_Success() throws Exception {
        Customer customer = Customer.builder()
                .customerId("cust-int-hold")
                .fullName("Jane Smith")
                .email("jane@example.com")
                .hashedEmail("hashed_jane_int")
                .walletBalance(new BigDecimal("100.00"))
                .loyaltyPoints(0)
                .vipStatus(false)
                .trustScore(100)
                .isAnonymized(false)
                .isOnProbation(false)
                .consecutiveOrdersCompleted(0)
                .version(0L)
                .build();
        entityManager.persist(customer);

        OrderEntity order = OrderEntity.builder()
                .customer(customer)
                .store(store)
                .totalAmount(new BigDecimal("299.99"))
                .paymentMethod("Wallet")
                .status("pending")
                .version(0)
                .build();
        entityManager.persist(order);
        entityManager.flush();

        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion = AgentSuggestionEntity.builder()
                .id(suggestionId)
                .agent(riskAgent)
                .domain("risk")
                .entityId("order_id=" + order.getOrderId())
                .recommendation("{\"action\":\"hold_order\",\"order_id\":" + order.getOrderId() + ",\"version\":0}")
                .status("approved")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .confidence(BigDecimal.valueOf(0.95))
                .reason("potential billing fraud")
                .impact("high")
                .build();
        agentSuggestionRepo.save(suggestion);

        PolicyDecision decision = PolicyDecision.builder()
                .suggestion(suggestion)
                .decision("approved")
                .policyVersion("v1")
                .reason("approved")
                .decidedBy("policy_engine")
                .build();
        policyDecisionRepo.save(decision);

        entityManager.flush();

        executionGateway.execute(suggestionId, "analyst-bob");

        AgentSuggestionEntity updatedSuggestion = agentSuggestionRepo.findById(suggestionId).orElseThrow();
        assertEquals("executed", updatedSuggestion.getStatus());

        entityManager.clear();
        OrderEntity updatedOrder = entityManager.find(OrderEntity.class, order.getOrderId());
        assertEquals("held", updatedOrder.getStatus());
        assertEquals(1, updatedOrder.getVersion());
        assertNotNull(updatedOrder.getUpdatedAt());
    }

    @Test
    public void testExecute_HoldOrder_StateDrift() throws Exception {
        Customer customer = Customer.builder()
                .customerId("cust-int-hold-drift")
                .fullName("Jane Smith")
                .email("jane-drift@example.com")
                .hashedEmail("hashed_jane_drift_int")
                .walletBalance(new BigDecimal("100.00"))
                .loyaltyPoints(0)
                .vipStatus(false)
                .trustScore(100)
                .isAnonymized(false)
                .isOnProbation(false)
                .consecutiveOrdersCompleted(0)
                .version(0L)
                .build();
        entityManager.persist(customer);

        OrderEntity order = OrderEntity.builder()
                .customer(customer)
                .store(store)
                .totalAmount(new BigDecimal("299.99"))
                .paymentMethod("Wallet")
                .status("pending")
                .version(1)
                .build();
        entityManager.persist(order);
        entityManager.flush();

        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion = AgentSuggestionEntity.builder()
                .id(suggestionId)
                .agent(riskAgent)
                .domain("risk")
                .entityId("order_id=" + order.getOrderId())
                .recommendation("{\"action\":\"hold_order\",\"order_id\":" + order.getOrderId() + ",\"version\":0}")
                .status("approved")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .confidence(BigDecimal.valueOf(0.95))
                .reason("potential billing fraud")
                .impact("high")
                .build();
        agentSuggestionRepo.save(suggestion);

        PolicyDecision decision = PolicyDecision.builder()
                .suggestion(suggestion)
                .decision("approved")
                .policyVersion("v1")
                .reason("approved")
                .decidedBy("policy_engine")
                .build();
        policyDecisionRepo.save(decision);

        entityManager.flush();

        assertThrows(OptimisticLockException.class, () -> {
            executionGateway.execute(suggestionId, "analyst-bob");
        });
    }
}
