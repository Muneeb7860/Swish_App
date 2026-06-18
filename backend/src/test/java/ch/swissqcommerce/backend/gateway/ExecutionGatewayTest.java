package ch.swissqcommerce.backend.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.agent.AgentSuggestion;
import ch.swissqcommerce.backend.model.AgentEventLog;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.policy.PolicyDecision;
import ch.swissqcommerce.backend.repository.AgentEventLogRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExecutionGatewayTest {

    @Mock
    private AgentEventLogRepository eventLogRepo;

    @Mock
    private HitlQueueRepository hitlQueueRepo;

    private ExecutionGateway executionGateway;

    @BeforeEach
    public void setUp() {
        executionGateway = new ExecutionGateway(eventLogRepo, hitlQueueRepo, new ObjectMapper());
    }

    @Test
    public void testProcess_Approved() {
        AgentSuggestion suggestion = AgentSuggestion.of("pricing", "increase price of coffee by 2%", 0.9, "reason", "low");
        PolicyDecision decision = PolicyDecision.approved("Within threshold");

        AgentEventLog expectedLog = AgentEventLog.builder().id(1L).executed(true).build();
        when(eventLogRepo.save(any(AgentEventLog.class))).thenReturn(expectedLog);

        AgentEventLog result = executionGateway.process("PricingAgent", suggestion, decision, "Input");

        assertNotNull(result);
        assertEquals(expectedLog.getId(), result.getId());
        
        ArgumentCaptor<AgentEventLog> logCaptor = ArgumentCaptor.forClass(AgentEventLog.class);
        verify(eventLogRepo).save(logCaptor.capture());
        AgentEventLog saved = logCaptor.getValue();
        assertEquals("PricingAgent", saved.getAgent());
        assertEquals("pricing", saved.getDomain());
        assertEquals("approved", saved.getPolicyStatus());
        assertTrue(saved.getExecuted());

        verifyNoInteractions(hitlQueueRepo);
    }

    @Test
    public void testProcess_NeedsHuman() {
        AgentSuggestion suggestion = AgentSuggestion.of("pricing", "increase price of coffee by 12%", 0.9, "reason", "medium");
        PolicyDecision decision = PolicyDecision.needsHuman("Requires manager review");

        AgentEventLog expectedLog = AgentEventLog.builder().id(2L).executed(false).build();
        when(eventLogRepo.save(any(AgentEventLog.class))).thenReturn(expectedLog);

        AgentEventLog result = executionGateway.process("PricingAgent", suggestion, decision, "Input");

        assertNotNull(result);
        assertEquals(expectedLog.getId(), result.getId());

        ArgumentCaptor<AgentEventLog> logCaptor = ArgumentCaptor.forClass(AgentEventLog.class);
        verify(eventLogRepo).save(logCaptor.capture());
        AgentEventLog saved = logCaptor.getValue();
        assertEquals("needs_human", saved.getPolicyStatus());
        assertFalse(saved.getExecuted());

        ArgumentCaptor<HitlQueue> ticketCaptor = ArgumentCaptor.forClass(HitlQueue.class);
        verify(hitlQueueRepo).save(ticketCaptor.capture());
        HitlQueue ticket = ticketCaptor.getValue();
        assertNotNull(ticket.getTicketId());
        assertTrue(ticket.getTicketId().startsWith("AGENT-"));
        assertEquals("agent_pricing", ticket.getType());
        assertEquals("pending", ticket.getStatus());
        assertTrue(ticket.getDescription().contains("increase price of coffee"));
    }

    @Test
    public void testProcess_Rejected() {
        AgentSuggestion suggestion = AgentSuggestion.of("pricing", "increase price of coffee by 25%", 0.9, "reason", "high");
        PolicyDecision decision = PolicyDecision.rejected("Exceeds max allowed");

        AgentEventLog expectedLog = AgentEventLog.builder().id(3L).executed(false).build();
        when(eventLogRepo.save(any(AgentEventLog.class))).thenReturn(expectedLog);

        AgentEventLog result = executionGateway.process("PricingAgent", suggestion, decision, "Input");

        assertNotNull(result);
        assertEquals(expectedLog.getId(), result.getId());

        ArgumentCaptor<AgentEventLog> logCaptor = ArgumentCaptor.forClass(AgentEventLog.class);
        verify(eventLogRepo).save(logCaptor.capture());
        AgentEventLog saved = logCaptor.getValue();
        assertEquals("rejected", saved.getPolicyStatus());
        assertFalse(saved.getExecuted());

        verifyNoInteractions(hitlQueueRepo);
    }
}
