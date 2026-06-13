package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.service.*;
import ch.swissqcommerce.backend.domain.agent.port.in.AgentUseCase;
import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MasterOrchestratorServiceTest {

    @Mock private CustomerSupportAgent customerSupportAgent;
    @Mock private AgentToolExecutor agentToolExecutor;
    @Mock private AgentOutPort agentOutPort;
    @Mock private EventUseCase eventUseCase;

    @Mock private B2BProcurementAgent b2BProcurementAgent;
    @Mock private B2BProcurementActivities b2BProcurementActivities;
    @Mock private ProcurementGuardrailsEngine procurementGuardrailsEngine;
    @Mock private WholesalerPort wholesalerPort;
    @Mock private DarkStoreRepository darkStoreRepository;
    @Mock private B2BRestockOrderPort restockOrderPort;
    @Mock private GovernanceUseCase governanceUseCase;
    // MeterRegistry is injected via @RequiredArgsConstructor.
    // @PostConstruct is NOT called by @InjectMocks so budgetGuardrailCounter
    // stays null — the null-guard in the service handles this gracefully.
    @Mock private MeterRegistry meterRegistry;
    @Mock private ch.swissqcommerce.backend.domain.agent.port.out.NegotiationArchivePort negotiationArchivePort;

    @InjectMocks
    private MasterOrchestratorService masterOrchestratorService;

    @BeforeEach
    public void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                masterOrchestratorService, "b2BProcurementActivities", b2BProcurementActivities);
    }

    @Test
    public void testProcessMessage_NormalFlowHighConfidence() {
        AgentRequest request = new AgentRequest("Hello, status of my orders?", "conv-123", "cust-456");
        CustomerSupportAgent.AgentAnalysis analysis = new CustomerSupportAgent.AgentAnalysis(
                "I am happy to assist you.", 0.95, null, null, 0.05
        );

        when(customerSupportAgent.analyze(request)).thenReturn(analysis);

        AgentResponse response = masterOrchestratorService.processMessage(request);

        assertNotNull(response);
        assertEquals("I am happy to assist you.", response.getReply());
        assertEquals(0.95, response.getConfidenceScore());
        assertEquals(0.05, response.getTokenCost());
        assertFalse(response.isHitlStatus());
        assertNull(response.getTicketId());

        verify(agentOutPort, never()).saveHitlQueue(any(HitlQueue.class));
        verify(eventUseCase, times(1)).publishEvent(eq("agent.message_processed"), anyString());
    }

    @Test
    public void testProcessMessage_LowConfidenceEscalation() {
        AgentRequest request = new AgentRequest("Refund my order immediately!", "conv-123", "cust-456");
        CustomerSupportAgent.AgentAnalysis analysis = new CustomerSupportAgent.AgentAnalysis(
                "Checking...", 0.60, null, null, 0.05
        );

        Customer customer = Customer.builder()
                .customerId("cust-456")
                .fullName("John Doe")
                .hashedEmail("hashed-email")
                .build();

        when(customerSupportAgent.analyze(request)).thenReturn(analysis);
        when(agentOutPort.findCustomerById("cust-456")).thenReturn(Optional.of(customer));

        AgentResponse response = masterOrchestratorService.processMessage(request);

        assertNotNull(response);
        assertEquals("Checking...", response.getReply());
        assertEquals(0.60, response.getConfidenceScore());
        assertTrue(response.isHitlStatus());
        assertNotNull(response.getTicketId());

        ArgumentCaptor<HitlQueue> ticketCaptor = ArgumentCaptor.forClass(HitlQueue.class);
        verify(agentOutPort, times(1)).saveHitlQueue(ticketCaptor.capture());

        HitlQueue ticket = ticketCaptor.getValue();
        assertEquals("agent_escalation", ticket.getType());
        assertEquals("pending", ticket.getStatus());
        assertEquals(customer, ticket.getCustomer());
        assertTrue(ticket.getDescription().contains("Low confidence score: 0.6"));

        verify(eventUseCase, times(1)).publishEvent(eq("agent.message_processed"), anyString());
        verify(eventUseCase, times(1)).publishEvent(eq("agent.hitl_escalated"), anyString());
    }

    @Test
    public void testProcessMessage_DailyBudgetLimitExceeded() {
        AgentRequest request1 = new AgentRequest("High cost query", "conv-1", "cust-1");
        CustomerSupportAgent.AgentAnalysis highCostAnalysis = new CustomerSupportAgent.AgentAnalysis(
                "Initial reply", 0.95, null, null, 6.00
        );

        when(customerSupportAgent.analyze(request1)).thenReturn(highCostAnalysis);

        // First call sets dailyCost to 6.00 which is >= 5.0
        AgentResponse response1 = masterOrchestratorService.processMessage(request1);
        assertNotNull(response1);
        assertFalse(response1.isHitlStatus());

        // Second request triggers the daily budget block
        AgentRequest request2 = new AgentRequest("Next query", "conv-2", "cust-2");
        
        AgentResponse response2 = masterOrchestratorService.processMessage(request2);

        assertNotNull(response2);
        assertEquals("System limit reached. Your request is routed to a customer support agent.", response2.getReply());
        assertEquals(0.0, response2.getConfidenceScore());
        assertEquals(0.0, response2.getTokenCost());
        assertTrue(response2.isHitlStatus());
        assertNotNull(response2.getTicketId());

        ArgumentCaptor<HitlQueue> ticketCaptor = ArgumentCaptor.forClass(HitlQueue.class);
        verify(agentOutPort, times(1)).saveHitlQueue(ticketCaptor.capture());

        HitlQueue ticket = ticketCaptor.getValue();
        assertEquals("agent_escalation", ticket.getType());
        assertEquals("pending", ticket.getStatus());
        assertTrue(ticket.getDescription().contains("Daily budget limit of $5 exceeded"));
    }

    @Test
    public void testNegotiateProcurement_NoActiveWholesalers() {
        AgentUseCase.NegotiationRequest req = new AgentUseCase.NegotiationRequest();
        req.setItemId("item-1");
        req.setItemName("Milk");
        req.setBasePrice(1.50);
        req.setQuantity(100);

        when(wholesalerPort.findAll()).thenReturn(Collections.emptyList());

        AgentUseCase.NegotiationResponse resp = masterOrchestratorService.negotiateProcurement(req);

        assertNotNull(resp);
        assertFalse(resp.isApproved());
        assertEquals("No active wholesalers found.", resp.getMessage());
    }

    @Test
    public void testNegotiateProcurement_SuccessWithGuardrailsApproved() {
        AgentUseCase.NegotiationRequest req = new AgentUseCase.NegotiationRequest();
        req.setItemId("item-1");
        req.setItemName("Milk");
        req.setBasePrice(1.50);
        req.setQuantity(100);

        Wholesaler wholesaler = Wholesaler.builder()
                .wholesalerId("wh-1")
                .name("Wholesaler A")
                .isActive(true)
                .trustScore(80)
                .build();

        when(wholesalerPort.findAll()).thenReturn(Arrays.asList(wholesaler));
        
        B2BProcurementAgent.NegotiationAnalysis analysis = new B2BProcurementAgent.NegotiationAnalysis(
            1.40, 0.90, "Bulk discount", "Bid matches requirement", 0.02
        );
        when(b2BProcurementActivities.callLlmNegotiation(any(), any(), anyDouble(), any())).thenReturn(analysis);
        when(b2BProcurementAgent.negotiateRestock(any(), any(), anyDouble(), any(), anyInt())).thenReturn(analysis);

        ProcurementGuardrailsEngine.GuardrailResult guardrailResult = new ProcurementGuardrailsEngine.GuardrailResult(true, "Price satisfies threshold.");
        when(procurementGuardrailsEngine.validate(anyDouble(), anyDouble(), anyInt())).thenReturn(guardrailResult);

        AgentUseCase.NegotiationResponse resp = masterOrchestratorService.negotiateProcurement(req);

        assertNotNull(resp);
        assertTrue(resp.isApproved());
        assertTrue(resp.getMessage().contains("RFQ AUCTION WINNER: Wholesaler A"));
        assertEquals(1.40, resp.getProposedPrice());
    }

    @Test
    public void testNegotiateProcurement_LlmNegotiationFailureFallback() {
        AgentUseCase.NegotiationRequest req = new AgentUseCase.NegotiationRequest();
        req.setItemId("item-1");
        req.setItemName("Milk");
        req.setBasePrice(10.00);
        req.setQuantity(5);

        Wholesaler wholesaler = Wholesaler.builder()
                .wholesalerId("wh-1")
                .name("Wholesaler A")
                .isActive(true)
                .trustScore(80)
                .build();

        when(wholesalerPort.findAll()).thenReturn(Arrays.asList(wholesaler));
        when(b2BProcurementActivities.callLlmNegotiation(any(), any(), anyDouble(), any())).thenThrow(new RuntimeException("LLM offline"));

        B2BProcurementAgent.NegotiationAnalysis fallbackOutcome = new B2BProcurementAgent.NegotiationAnalysis(
                9.00, 0.50, "Rule-based fallback (LLM offline)", "COUNTER_OFFER", 0.0
        );
        when(b2BProcurementAgent.negotiateRestock(any(), any(), anyDouble(), any(), anyInt())).thenReturn(fallbackOutcome);

        ProcurementGuardrailsEngine.GuardrailResult guardrailResult = new ProcurementGuardrailsEngine.GuardrailResult(true, "Price satisfies threshold.");
        when(procurementGuardrailsEngine.validate(anyDouble(), anyDouble(), anyInt())).thenReturn(guardrailResult);

        AgentUseCase.NegotiationResponse resp = masterOrchestratorService.negotiateProcurement(req);

        assertNotNull(resp);
        assertTrue(resp.isApproved());
        assertEquals(9.00, resp.getProposedPrice(), 0.01);
        assertEquals(0.50, resp.getConfidence(), 0.01);
        assertEquals("Rule-based fallback (LLM offline)", resp.getRationale());
    }
}
