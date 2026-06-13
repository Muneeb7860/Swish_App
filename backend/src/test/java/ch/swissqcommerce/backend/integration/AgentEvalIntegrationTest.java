package ch.swissqcommerce.backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.service.*;
import ch.swissqcommerce.backend.domain.agent.port.in.AgentUseCase;
import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.HitlQueue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Agent eval layer — integration-level checks on the AI orchestration stack.
 *
 * <p>Addresses the O'Reilly "AI Agents Stack 2026" eval gap: validates routing accuracy, HITL
 * calibration, budget guardrails, RFQ auction winner selection, and Prometheus observability — all
 * without a live LLM.
 *
 * <p>MasterOrchestratorService is the real Spring bean; only the LLM adapters
 * (CustomerSupportAgent, B2BProcurementAgent, ProcurementGuardrailsEngine) are mocked to keep tests
 * hermetic.
 *
 * <p>IMPORTANT: evalBudgetGuardrail runs last (@Order 99) because it leaves
 * MasterOrchestratorService.dailyCost = 6.0 on the singleton, which would poison earlier tests if
 * they ran in a different order.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AgentEvalIntegrationTest {

    // ─── In-memory cache override (no Redis needed) ──────────────────────────
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

    // ─── Mocked LLM + outbound ports ─────────────────────────────────────────
    @MockBean private CustomerSupportAgent customerSupportAgent;
    @MockBean private B2BProcurementAgent b2BProcurementAgent;
    @MockBean private B2BProcurementActivities b2BProcurementActivities;
    @MockBean private ProcurementGuardrailsEngine procurementGuardrailsEngine;
    @MockBean private AgentOutPort agentOutPort;
    @MockBean private EventUseCase eventUseCase;
    @MockBean private WholesalerPort wholesalerPort;
    @MockBean private B2BRestockOrderPort restockOrderPort;
    @MockBean private GovernanceUseCase governanceUseCase;

    // Real beans under test
    @Autowired private AgentUseCase agentUseCase;
    @Autowired private MeterRegistry meterRegistry;

    @BeforeEach
    void resetMocks() {
        reset(
                customerSupportAgent,
                b2BProcurementAgent,
                procurementGuardrailsEngine,
                agentOutPort,
                eventUseCase,
                wholesalerPort,
                restockOrderPort,
                governanceUseCase);
        // saveHitlQueue returns HitlQueue — mock returns null; result is never used by caller
        when(agentOutPort.saveHitlQueue(any())).thenReturn(null);
        // publishEvent returns DomainEventEntity — mock returns null; result is never used by
        // caller
        when(eventUseCase.publishEvent(anyString(), anyString())).thenReturn(null);
        // findCustomerById returns Optional.empty by default (Mockito default); override in tests
        // that need a customer
        // Default guardrail: auto-approve all negotiation requests
        when(procurementGuardrailsEngine.validate(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(
                        new ProcurementGuardrailsEngine.GuardrailResult(
                                true, "Auto-approved in test"));
    }

    // ─── EVAL 1: Tool selection — order query should route to ORDER_STATUS ────

    @Test
    @Order(1)
    public void evalToolSelection_OrderQuery_RoutesToOrderStatusTool() {
        // LLM decides ORDER_STATUS tool is needed (tool = "ORDER_STATUS")
        CustomerSupportAgent.AgentAnalysis firstPass =
                new CustomerSupportAgent.AgentAnalysis(
                        "Let me check that for you.", 0.80, "ORDER_STATUS", "42", 0.01);
        CustomerSupportAgent.AgentAnalysis finalPass =
                new CustomerSupportAgent.AgentAnalysis(
                        "Your order 42 is currently in shipping.", 0.92, null, null, 0.02);
        // Use any() to avoid strict-stub argument-matching issues
        when(customerSupportAgent.analyze(any())).thenReturn(firstPass);
        when(customerSupportAgent.generateFinalResponse(any(), anyString(), anyDouble()))
                .thenReturn(finalPass);
        when(agentOutPort.findOrderById(42)).thenReturn(Optional.empty());

        AgentRequest req =
                new AgentRequest("What is the status of my order 42?", "conv-1", "cust-1");
        AgentResponse response = agentUseCase.processMessage(req);

        // Verify tool execution branch was taken
        verify(customerSupportAgent, times(1))
                .generateFinalResponse(any(), anyString(), anyDouble());
        assertNotNull(response.getReply());
        assertFalse(response.isHitlStatus(), "High-confidence order query must not trigger HITL");
        assertEquals(0.92, response.getConfidenceScore(), 0.001);
    }

    // ─── EVAL 2: Tool selection — general greeting should select no tool ──────

    @Test
    @Order(2)
    public void evalToolSelection_GeneralGreeting_NoToolSelected() {
        CustomerSupportAgent.AgentAnalysis analysis =
                new CustomerSupportAgent.AgentAnalysis(
                        "Hello! How can I help you today?", 0.95, null, null, 0.005);
        when(customerSupportAgent.analyze(any())).thenReturn(analysis);

        AgentRequest req = new AgentRequest("Hello!", "conv-2", "cust-2");
        AgentResponse response = agentUseCase.processMessage(req);

        // generateFinalResponse must NOT be called — no tool execution path
        verify(customerSupportAgent, never()).generateFinalResponse(any(), any(), anyDouble());
        assertFalse(response.isHitlStatus(), "High-confidence greeting must not trigger HITL");
        assertEquals("Hello! How can I help you today?", response.getReply());
    }

    // ─── EVAL 3: HITL calibration — confidence < 0.70 must escalate ──────────

    @Test
    @Order(3)
    public void evalHitlCalibration_ConfidenceBelow70_EscalatesHitl() {
        CustomerSupportAgent.AgentAnalysis analysis =
                new CustomerSupportAgent.AgentAnalysis(
                        "I understand your concern.", 0.65, null, null, 0.01);
        Customer customer =
                Customer.builder()
                        .customerId("cust-3")
                        .fullName("Eva Test")
                        .hashedEmail("hash3")
                        .build();

        when(customerSupportAgent.analyze(any())).thenReturn(analysis);
        when(agentOutPort.findCustomerById(anyString())).thenReturn(Optional.of(customer));

        AgentRequest req = new AgentRequest("I demand a refund now!", "conv-3", "cust-3");
        AgentResponse response = agentUseCase.processMessage(req);

        assertTrue(response.isHitlStatus(), "Confidence 0.65 must trigger HITL");
        assertNotNull(response.getTicketId(), "HITL ticket ID must be non-null");

        ArgumentCaptor<HitlQueue> cap = ArgumentCaptor.forClass(HitlQueue.class);
        verify(agentOutPort).saveHitlQueue(cap.capture());
        assertEquals("agent_escalation", cap.getValue().getType());
        assertEquals("pending", cap.getValue().getStatus());
        assertTrue(
                cap.getValue().getDescription().contains("Low confidence score: 0.65"),
                "Ticket description must mention confidence score. Actual: "
                        + cap.getValue().getDescription());
    }

    // ─── EVAL 4: HITL threshold — confidence exactly 0.70 must NOT escalate ───

    @Test
    @Order(4)
    public void evalHitlCalibration_ConfidenceExactly70_NoHitl() {
        CustomerSupportAgent.AgentAnalysis analysis =
                new CustomerSupportAgent.AgentAnalysis(
                        "Your delivery is on its way.", 0.70, null, null, 0.01);
        when(customerSupportAgent.analyze(any())).thenReturn(analysis);

        AgentRequest req = new AgentRequest("Track my delivery please", "conv-4", "cust-4");
        AgentResponse response = agentUseCase.processMessage(req);

        assertFalse(
                response.isHitlStatus(),
                "Confidence exactly 0.70 must NOT trigger HITL — threshold is strictly < 0.70");
        verify(agentOutPort, never()).saveHitlQueue(any());
    }

    // ─── EVAL 5: Negotiation — lowest bid wins in RFQ auction ─────────────────

    @Test
    @Order(5)
    public void evalNegotiation_LowestBidWins_RfqAuction() {
        Wholesaler cheapWholesaler =
                Wholesaler.builder()
                        .wholesalerId("w-cheap")
                        .name("BestPrice Supplier")
                        .isActive(true)
                        .trustScore(75)
                        .build();
        Wholesaler expensiveWholesaler =
                Wholesaler.builder()
                        .wholesalerId("w-exp")
                        .name("PremiumSupply AG")
                        .isActive(true)
                        .trustScore(90)
                        .build();

        when(wholesalerPort.findAll())
                .thenReturn(Arrays.asList(cheapWholesaler, expensiveWholesaler));

        B2BProcurementAgent.NegotiationAnalysis cheapAnalysis =
                new B2BProcurementAgent.NegotiationAnalysis(
                        1.60, 0.88, "Volume discount", "ACCEPTED", 0.01);
        B2BProcurementAgent.NegotiationAnalysis expensiveAnalysis =
                new B2BProcurementAgent.NegotiationAnalysis(
                        1.85, 0.95, "Premium quality", "ACCEPTED", 0.01);

        when(b2BProcurementActivities.callLlmNegotiation(
                        any(), any(), anyDouble(), eq("BestPrice Supplier")))
                .thenReturn(cheapAnalysis);
        when(b2BProcurementActivities.callLlmNegotiation(
                        any(), any(), anyDouble(), eq("PremiumSupply AG")))
                .thenReturn(expensiveAnalysis);

        when(b2BProcurementAgent.negotiateRestock(
                        any(), any(), anyDouble(), eq("BestPrice Supplier"), anyInt()))
                .thenReturn(cheapAnalysis);
        when(b2BProcurementAgent.negotiateRestock(
                        any(), any(), anyDouble(), eq("PremiumSupply AG"), anyInt()))
                .thenReturn(expensiveAnalysis);

        AgentUseCase.NegotiationRequest req = new AgentUseCase.NegotiationRequest();
        req.setItemId("item-milk");
        req.setItemName("Organic Milk");
        req.setBasePrice(2.00);
        req.setQuantity(200);

        AgentUseCase.NegotiationResponse resp = agentUseCase.negotiateProcurement(req);

        assertNotNull(resp);
        assertTrue(
                resp.getMessage().contains("BestPrice Supplier"),
                "Cheapest wholesaler must win the RFQ auction. Message: " + resp.getMessage());
        assertEquals(1.60, resp.getProposedPrice(), 0.001, "Winning bid must be the lowest price");
    }

    // ─── EVAL 6: Negotiation — trust score tiebreaker on equal price ──────────

    @Test
    @Order(6)
    public void evalNegotiation_TrustScoreTiebreaker_HigherTrustWins() {
        Wholesaler lowTrust =
                Wholesaler.builder()
                        .wholesalerId("w-lt")
                        .name("LowTrust Corp")
                        .isActive(true)
                        .trustScore(50)
                        .build();
        Wholesaler highTrust =
                Wholesaler.builder()
                        .wholesalerId("w-ht")
                        .name("HighTrust AG")
                        .isActive(true)
                        .trustScore(90)
                        .build();

        when(wholesalerPort.findAll()).thenReturn(Arrays.asList(lowTrust, highTrust));

        B2BProcurementAgent.NegotiationAnalysis lowTrustAnalysis =
                new B2BProcurementAgent.NegotiationAnalysis(
                        4.50, 0.80, "Stable supply", "ACCEPTED", 0.01);
        B2BProcurementAgent.NegotiationAnalysis highTrustAnalysis =
                new B2BProcurementAgent.NegotiationAnalysis(
                        4.50, 0.85, "Premium reliability", "ACCEPTED", 0.01);

        // Both quote the same price — tiebreak by trust score
        when(b2BProcurementActivities.callLlmNegotiation(
                        any(), any(), anyDouble(), eq("LowTrust Corp")))
                .thenReturn(lowTrustAnalysis);
        when(b2BProcurementActivities.callLlmNegotiation(
                        any(), any(), anyDouble(), eq("HighTrust AG")))
                .thenReturn(highTrustAnalysis);

        when(b2BProcurementAgent.negotiateRestock(
                        any(), any(), anyDouble(), eq("LowTrust Corp"), anyInt()))
                .thenReturn(lowTrustAnalysis);
        when(b2BProcurementAgent.negotiateRestock(
                        any(), any(), anyDouble(), eq("HighTrust AG"), anyInt()))
                .thenReturn(highTrustAnalysis);

        AgentUseCase.NegotiationRequest req = new AgentUseCase.NegotiationRequest();
        req.setItemId("item-eggs");
        req.setItemName("Free-range Eggs");
        req.setBasePrice(5.00);
        req.setQuantity(50);

        AgentUseCase.NegotiationResponse resp = agentUseCase.negotiateProcurement(req);

        assertNotNull(resp);
        assertTrue(
                resp.getMessage().contains("HighTrust AG"),
                "When prices are equal, higher trust score must win. Message: "
                        + resp.getMessage());
    }

    // ─── EVAL 7: Prometheus gauge registered for anomaly ratio ───────────────

    @Test
    @Order(7)
    public void evalPrometheusGauge_AnomalyRatioRegistered() {
        // SecurityController.@PostConstruct registers this gauge at startup
        Gauge gauge = meterRegistry.find("security.anomaly.analyzed.ratio").gauge();
        assertNotNull(
                gauge,
                "Prometheus gauge 'security.anomaly.analyzed.ratio' must be registered at startup");

        double ratio = gauge.value();
        assertTrue(
                ratio >= 0.0 && ratio <= 1.0,
                "Anomaly ratio must be in [0.0, 1.0]. Actual: " + ratio);
    }

    // ─── EVAL 8: Budget guardrail — daily spend ≥ $5 blocks subsequent requests
    // Must run LAST: it permanently sets dailyCost = 6.0 on the singleton bean.

    @Test
    @Order(99)
    public void evalBudgetGuardrail_DailyBudgetExceeded_BlocksSubsequentRequest() {
        // Snapshot the counter BEFORE the test fires the guardrail
        Counter guardrailCounter = meterRegistry.find("agent.budget.guardrail.triggers").counter();
        assertNotNull(
                guardrailCounter,
                "Prometheus counter 'agent.budget.guardrail.triggers' must be registered at"
                        + " startup");
        double counterBefore = guardrailCounter.count();

        // First request burns the $5 daily budget
        CustomerSupportAgent.AgentAnalysis highCost =
                new CustomerSupportAgent.AgentAnalysis("Done.", 0.99, null, null, 6.00);
        when(customerSupportAgent.analyze(any())).thenReturn(highCost);

        AgentRequest req1 = new AgentRequest("Expensive query", "conv-5a", "cust-5");
        agentUseCase.processMessage(req1); // dailyCost → 6.00

        // Second request — budget guard fires before LLM is even consulted
        AgentRequest req2 = new AgentRequest("Any query", "conv-5b", "cust-5");
        AgentResponse blocked = agentUseCase.processMessage(req2);

        assertEquals(
                "System limit reached. Your request is routed to a customer support agent.",
                blocked.getReply(),
                "Budget guardrail must block requests after $5 daily limit");
        assertTrue(blocked.isHitlStatus(), "Blocked request must be flagged as HITL");
        assertEquals(0.0, blocked.getTokenCost(), "Blocked request incurs zero token cost");
        assertNotNull(blocked.getTicketId(), "Blocked request must produce a HITL ticket ID");

        // Verify the counter was incremented exactly once (req2 triggered the guardrail)
        assertEquals(
                counterBefore + 1,
                guardrailCounter.count(),
                0.001,
                "Budget guardrail counter must increment by 1 when the limit fires");
    }
}
