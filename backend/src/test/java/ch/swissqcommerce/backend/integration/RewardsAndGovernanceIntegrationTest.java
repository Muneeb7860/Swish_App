package ch.swissqcommerce.backend.integration;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalRepository;
import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.domain.reward.port.in.RewardUseCase;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import ch.swissqcommerce.backend.domain.reward.core.service.RiderLeaderboardService;
import ch.swissqcommerce.backend.domain.reward.adapter.out.persistence.CustomerLoyaltyRepository;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WholesalerRepository;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.B2BRestockOrderRepository;
import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogRepository;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.agent.adapter.in.web.AgentController;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class RewardsAndGovernanceIntegrationTest {

    @Autowired
    private RewardUseCase rewardUseCase;

    @Autowired
    private GovernanceUseCase governanceUseCase;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WholesalerRepository wholesalerRepository;

    @Autowired
    private B2BRestockOrderRepository restockOrderRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderTelemetryLogRepository telemetryLogRepository;

    @Autowired
    private ProcurementApprovalRepository approvalsRepository;

    @Autowired
    private CustomerLoyaltyRepository loyaltyRepository;

    @Autowired
    private RewardOutPort rewardOutPort;

    @Autowired
    private RiderLeaderboardService leaderboardService;

    @Autowired
    private DarkStoreRepository darkStoreRepository;

    @Autowired
    private AgentController agentController;

    @MockBean
    private ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementAgent b2BProcurementAgent;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private Customer customer;
    private Wholesaler wholesaler;
    private B2BRestockOrder restockOrder;
    private Order order;

    @BeforeEach
    void setUp() {
        // Mock ZSet operations for leaderboard
        ZSetOperations zSetOps = Mockito.mock(ZSetOperations.class);
        Mockito.when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        // Seed DarkStore if not exists
        if (darkStoreRepository.count() == 0) {
            DarkStore store = DarkStore.builder()
                    .storeId("store-1")
                    .storeName("Zurich Hub")
                    .address("Bahnhofstrasse")
                    .latitude(BigDecimal.valueOf(47.3769))
                    .longitude(BigDecimal.valueOf(8.5417))
                    .build();
            darkStoreRepository.save(store);
        }

        // Seed Customer
        customer = Customer.builder()
                .customerId("CUST-999")
                .fullName("Hikaru Sulu")
                .email("sulu@starfleet.org")
                .hashedEmail("sulu_hash_999")
                .walletBalance(new BigDecimal("100.00"))
                .loyaltyPoints(0)
                .build();
        customerRepository.save(customer);

        // Seed Wholesaler
        wholesaler = Wholesaler.builder()
                .wholesalerId("WHOLE-999")
                .name("Starfleet Supplies")
                .build();
        wholesalerRepository.save(wholesaler);

        // Seed Restock Order
        restockOrder = B2BRestockOrder.builder()
                .wholesaler(wholesaler)
                .invoiceAmount(new BigDecimal("5200.00"))
                .isFallback(false)
                .status("pending")
                .idempotencyKey("KEY-HITL-1")
                .build();
        restockOrderRepository.save(restockOrder);

        // Seed Order
        order = Order.builder()
                .customer(customer)
                .totalAmount(new BigDecimal("25.00"))
                .paymentMethod("Wallet")
                .status("picked")
                .idempotencyKey("KEY-ORDER-1")
                .build();
        orderRepository.save(order);

        // Seed Telemetry logs for order
        OrderTelemetryLog log1 = OrderTelemetryLog.builder()
                .order(order)
                .deviceTimestamp(OffsetDateTime.now())
                .latitude(new BigDecimal("47.3769"))
                .longitude(new BigDecimal("8.5417"))
                .temperature(new BigDecimal("5.2"))
                .build();
        OrderTelemetryLog log2 = OrderTelemetryLog.builder()
                .order(order)
                .deviceTimestamp(OffsetDateTime.now().plusSeconds(5))
                .latitude(new BigDecimal("47.3770"))
                .longitude(new BigDecimal("8.5418"))
                .temperature(new BigDecimal("5.8"))
                .build();
        telemetryLogRepository.save(log1);
        telemetryLogRepository.save(log2);
    }

    @Test
    public void testCreditLoyaltyPointsAndVerifyLog() {
        // Given
        String customerId = customer.getCustomerId();

        // When
        rewardUseCase.addPoints(customerId, 50);

        // Then
        RewardPoints points = rewardOutPort.findRewardPointsByCustomerId(customerId).orElseThrow();
        assertEquals(50, points.getLoyaltyPoints());

        List<CustomerLoyalty> logs = loyaltyRepository.findByCustomerId(customerId);
        assertEquals(1, logs.size());
        assertEquals(50, logs.get(0).getPointsChanged());
        assertEquals("Points added via UseCase", logs.get(0).getDescription());
    }

    @Test
    public void testRiderLeaderboardRedisCall() {
        // When
        leaderboardService.updateRiderScore("rider-1", 10.0);

        // Then
        Mockito.verify(redisTemplate.opsForZSet()).incrementScore("rewards:rider:leaderboard", "rider-1", 10.0);
    }

    @Test
    public void testB2BRestockOverrideApproval() {
        // Given
        Integer restockOrderId = restockOrder.getRestockOrderId();
        BigDecimal amount = restockOrder.getInvoiceAmount();

        // When - Audit negotiation (request override)
        governanceUseCase.auditNegotiation(restockOrderId, "WHOLE-999", amount);

        // Then - Verify pending override request created
        List<ProcurementApproval> approvals = approvalsRepository.findAll();
        assertFalse(approvals.isEmpty());
        ProcurementApproval approval = approvals.get(0);
        assertEquals("PENDING", approval.getStatus());
        assertEquals(amount, approval.getAmount());

        // When - Approve override
        governanceUseCase.approveOverride(approval.getId(), "operator-admin", "Urgent restock approved");

        // Then - Verify approved and restock order released
        ProcurementApproval updatedApproval = approvalsRepository.findById(approval.getId()).orElseThrow();
        assertEquals("APPROVED", updatedApproval.getStatus());
        assertEquals("operator-admin", updatedApproval.getOverrideBy());
        assertEquals("Urgent restock approved", updatedApproval.getOverrideReason());

        B2BRestockOrder updatedRestock = restockOrderRepository.findById(restockOrderId).orElseThrow();
        assertEquals("fulfilled", updatedRestock.getStatus());
    }

    @Test
    public void testB2BRestockOverrideRejection() {
        // Given
        Integer restockOrderId = restockOrder.getRestockOrderId();
        BigDecimal amount = restockOrder.getInvoiceAmount();

        governanceUseCase.auditNegotiation(restockOrderId, "WHOLE-999", amount);
        ProcurementApproval approval = approvalsRepository.findAll().get(0);

        // When - Reject override
        governanceUseCase.rejectOverride(approval.getId(), "operator-admin", "Too expensive, reject");

        // Then - Verify rejected and restock order failed
        ProcurementApproval updatedApproval = approvalsRepository.findById(approval.getId()).orElseThrow();
        assertEquals("REJECTED", updatedApproval.getStatus());

        B2BRestockOrder updatedRestock = restockOrderRepository.findById(restockOrderId).orElseThrow();
        assertEquals("failed", updatedRestock.getStatus());
    }

    @Test
    public void testCryptographicSigningOfTelemetrySummary() {
        // Given
        String orderId = String.valueOf(order.getOrderId());

        // When
        String signature = governanceUseCase.signDeliverySummary(orderId, "mock-pod-hash-xyz");

        // Then
        assertNotNull(signature);
        assertFalse(signature.isEmpty());

        // Verify it is a valid Base64 encoded string
        byte[] decodedBytes = Base64.getDecoder().decode(signature);
        assertTrue(decodedBytes.length > 0);
    }

    @Test
    public void testB2BProcurementNegotiationGuardrailFailure() {
        // Given
        AgentController.NegotiationRequest request = new AgentController.NegotiationRequest();
        request.setItemId("item-1");
        request.setItemName("Swiss Milk Premium");
        request.setBasePrice(2.50);
        request.setWholesalerName("WHOLE-999");
        request.setQuantity(3000); // 3000 * 2.50 = 7500 CHF (exceeds 5000 CHF limit)
        request.setCustomerId("CUST-999");

        Mockito.when(b2BProcurementAgent.negotiateRestock(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyDouble(), Mockito.anyString()))
                .thenReturn(new ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementAgent.NegotiationAnalysis(
                        2.50, 0.95, "Good price", "ACCEPTED", 0.00005));

        // When
        ResponseEntity<AgentController.NegotiationResponse> responseEntity = agentController.negotiate(request);

        // Then
        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertFalse(responseEntity.getBody().isApproved());

        // Verify that B2BRestockOrder was created in pending state
        List<B2BRestockOrder> restockOrders = restockOrderRepository.findAll();
        // The one seeded in setUp is "KEY-HITL-1", we find the new one
        B2BRestockOrder pendingOrder = restockOrders.stream()
                .filter(o -> !"KEY-HITL-1".equals(o.getIdempotencyKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("pending", pendingOrder.getStatus());
        assertEquals(0, pendingOrder.getInvoiceAmount().compareTo(BigDecimal.valueOf(7500.00)));

        // Verify that ProcurementApproval request was created pointing to the restock order
        List<ProcurementApproval> approvals = approvalsRepository.findAll();
        ProcurementApproval pendingApproval = approvals.stream()
                .filter(a -> pendingOrder.getRestockOrderId().equals(a.getRestockOrderId()))
                .findFirst()
                .orElseThrow();
        assertEquals("PENDING", pendingApproval.getStatus());
        assertEquals(0, pendingApproval.getAmount().compareTo(BigDecimal.valueOf(7500.00)));
    }

    @Test
    public void testMultiWholesalerRfqAuction() {
        // Given
        Wholesaler otherWholesaler = Wholesaler.builder()
                .wholesalerId("WHOLE-888")
                .name("Galactic Supplies")
                .isActive(true)
                .build();
        wholesalerRepository.save(otherWholesaler);

        AgentController.NegotiationRequest request = new AgentController.NegotiationRequest();
        request.setItemId("item-1");
        request.setItemName("Swiss Milk Premium");
        request.setBasePrice(2.50);
        request.setWholesalerName("WHOLE-999"); 
        request.setQuantity(100); 
        request.setCustomerId("CUST-999");

        Mockito.when(b2BProcurementAgent.negotiateRestock(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyDouble(), Mockito.eq("Starfleet Supplies")))
                .thenReturn(new ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementAgent.NegotiationAnalysis(
                        2.40, 0.95, "Good price from Starfleet", "ACCEPTED", 0.00005));

        Mockito.when(b2BProcurementAgent.negotiateRestock(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyDouble(), Mockito.eq("Galactic Supplies")))
                .thenReturn(new ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementAgent.NegotiationAnalysis(
                        2.30, 0.95, "Cheaper price from Galactic", "ACCEPTED", 0.00005));

        // When
        ResponseEntity<AgentController.NegotiationResponse> responseEntity = agentController.negotiate(request);

        // Then
        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertTrue(responseEntity.getBody().isApproved());
        assertEquals(2.30, responseEntity.getBody().getProposedPrice(), 0.01);
        assertTrue(responseEntity.getBody().getMessage().contains("Galactic Supplies"));
    }
}
