package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.service.AgentToolExecutor;
import ch.swissqcommerce.backend.domain.agent.core.service.CustomerSupportAgent;
import ch.swissqcommerce.backend.domain.agent.core.service.MasterOrchestratorService;
import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MasterOrchestratorServiceTest {

    @Mock
    private CustomerSupportAgent customerSupportAgent;

    @Mock
    private AgentToolExecutor agentToolExecutor;

    @Mock
    private AgentOutPort agentOutPort;

    @Mock
    private EventUseCase eventUseCase;

    @InjectMocks
    private MasterOrchestratorService masterOrchestratorService;

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
}
