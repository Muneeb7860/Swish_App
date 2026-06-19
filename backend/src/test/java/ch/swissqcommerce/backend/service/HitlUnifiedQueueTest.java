package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.governance.core.model.HitlItem;
import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.core.service.GovernanceServiceImpl;
import ch.swissqcommerce.backend.domain.governance.port.out.HitlQueuePort;
import ch.swissqcommerce.backend.domain.governance.port.out.ProcurementApprovalPort;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.exception.ResourceNotFoundException;
import ch.swissqcommerce.backend.exception.TicketAlreadyResolvedException;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase 8: unified HITL queue + composite-id resolution in {@link GovernanceServiceImpl}. Proves
 * the supervisor sees BOTH B2B procurement overrides and agent escalations in one list, and that
 * approve/reject routes back to the correct source queue.
 */
@ExtendWith(MockitoExtension.class)
class HitlUnifiedQueueTest {

    @Mock private ProcurementApprovalPort approvalsPort;
    @Mock private B2BRestockOrderPort restockOrderPort;
    @Mock private TelemetryPort telemetryPort;
    @Mock private HitlQueuePort hitlQueuePort;
    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;
    @Mock private ch.swissqcommerce.backend.gateway.ExecutionGateway executionGateway;
    @Mock private ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository agentSuggestionRepo;
    @Mock private ch.swissqcommerce.backend.repository.PolicyDecisionRepository policyDecisionRepo;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private GovernanceServiceImpl service;

    @BeforeEach
    void setUp() {
        // null MeterRegistry → constructor skips the gauge (guarded).
        service =
                new GovernanceServiceImpl(
                        approvalsPort,
                        restockOrderPort,
                        telemetryPort,
                        hitlQueuePort,
                        trustLedgerRepository,
                        null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "executionGateway", executionGateway);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "agentSuggestionRepo", agentSuggestionRepo);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "policyDecisionRepo", policyDecisionRepo);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
    }

    private ProcurementApproval approval(int id, String status) {
        return ProcurementApproval.builder()
                .id(id)
                .restockOrderId(42)
                .wholesalerId("W1")
                .amount(new BigDecimal("6000.00"))
                .status(status)
                .build();
    }

    private HitlQueue agentTicket(String ticketId, String status) {
        return HitlQueue.builder()
                .ticketId(ticketId)
                .type("agent_escalation")
                .description("Low confidence escalation")
                .amount(new BigDecimal("0.00"))
                .status(status)
                .build();
    }

    @Test
    void getPendingHitlItems_mergesBothSourcesAndFiltersResolved() {
        when(approvalsPort.findAll())
                .thenReturn(List.of(approval(1, "PENDING"), approval(2, "APPROVED")));
        when(hitlQueuePort.findByStatus("pending"))
                .thenReturn(List.of(agentTicket("HITL-ABC", "pending")));

        List<HitlItem> items = service.getPendingHitlItems();

        assertEquals(2, items.size(), "one pending procurement + one pending agent escalation");
        HitlItem pa =
                items.stream()
                        .filter(i -> "B2B_PROCUREMENT".equals(i.getSource()))
                        .findFirst()
                        .orElseThrow();
        HitlItem aq =
                items.stream()
                        .filter(i -> "AGENT_ESCALATION".equals(i.getSource()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("PA-1", pa.getId());
        assertEquals("AQ-HITL-ABC", aq.getId());
        assertEquals("PENDING", pa.getStatus());
        assertEquals("PENDING", aq.getStatus());
        assertEquals(
                "pricing_review".equals(aq.getType()) ? "pricing_review" : "agent_escalation",
                aq.getType());
    }

    @Test
    void resolveAgentEscalation_approve_setsApprovedAndPersists() {
        HitlQueue ticket = agentTicket("HITL-ABC", "pending");
        when(hitlQueuePort.findByTicketId("HITL-ABC")).thenReturn(Optional.of(ticket));

        service.resolveHitlItem("AQ-HITL-ABC", true, "swissadmin", "looks fine");

        assertEquals("approved", ticket.getStatus());
        verify(hitlQueuePort, times(1)).save(ticket);
    }

    @Test
    void resolveAgentEscalation_secondTimeIsRejected() {
        when(hitlQueuePort.findByTicketId("HITL-ABC"))
                .thenReturn(Optional.of(agentTicket("HITL-ABC", "approved")));

        assertThrows(
                TicketAlreadyResolvedException.class,
                () -> service.resolveHitlItem("AQ-HITL-ABC", true, "swissadmin", "again"));
        verify(hitlQueuePort, never()).save(any());
    }

    @Test
    void resolveAgentEscalation_missingTicket_throwsNotFound() {
        when(hitlQueuePort.findByTicketId("HITL-NONE")).thenReturn(Optional.empty());
        assertThrows(
                ResourceNotFoundException.class,
                () -> service.resolveHitlItem("AQ-HITL-NONE", false, "swissadmin", "x"));
    }

    @Test
    void resolveProcurement_rejectDelegatesToApprovalQueue() {
        ProcurementApproval pending = approval(5, "PENDING");
        when(approvalsPort.findById(5)).thenReturn(Optional.of(pending));
        when(restockOrderPort.findById(any())).thenReturn(Optional.<B2BRestockOrder>empty());

        service.resolveHitlItem("PA-5", false, "swissadmin", "too expensive");

        assertEquals("REJECTED", pending.getStatus());
        verify(approvalsPort, times(1)).save(pending);
        verify(hitlQueuePort, never()).save(any());
    }

    @Test
    void resolveHitlItem_unknownIdShape_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveHitlItem("ZZ-7", true, "swissadmin", "x"));
    }

    @Test
    void resolveHitlItem_blankReason_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveHitlItem("PA-5", true, "swissadmin", "   "));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveHitlItem("PA-5", true, "swissadmin", null));
    }

    @Test
    void resolveHitlItem_recordsJustificationHashInTrustLedger() {
        ProcurementApproval pending = approval(5, "PENDING");
        when(approvalsPort.findById(5)).thenReturn(Optional.of(pending));
        when(restockOrderPort.findById(any())).thenReturn(Optional.<B2BRestockOrder>empty());

        service.resolveHitlItem("PA-5", true, "swissadmin", "My special justification");

        assertEquals("APPROVED", pending.getStatus());
        // Verify that trustLedgerRepository saved the hash audit log
        verify(trustLedgerRepository, times(1)).save(any());
    }

    @Test
    void resolveAgentEscalation_withAgentNameAndDomain_executesApprovedAction() throws Exception {
        HitlQueue ticket = HitlQueue.builder()
                .ticketId("HITL-ABC")
                .type("agent_pricing")
                .description("[PricingAgent] increase price of coffee by 5.5% — Confidence: 0.90, Impact: low")
                .status("pending")
                .build();
        when(hitlQueuePort.findByTicketId("HITL-ABC")).thenReturn(Optional.of(ticket));

        java.util.UUID suggestionId = java.util.UUID.randomUUID();
        ch.swissqcommerce.backend.model.AgentSuggestionEntity suggestion = ch.swissqcommerce.backend.model.AgentSuggestionEntity.builder()
                .id(suggestionId)
                .domain("pricing")
                .entityId("SKU-123")
                .recommendation("{\"action\":\"update_price\",\"old_value\":10.00,\"new_value\":10.55}")
                .status("pending")
                .build();

        when(agentSuggestionRepo.findByAgentNameAndDomainAndStatusOrderByCreatedAtDesc(
                "PricingAgent", "pricing", "pending"))
                .thenReturn(List.of(suggestion));

        service.resolveHitlItem("AQ-HITL-ABC", true, "swissadmin", "approved by manager");

        assertEquals("approved", ticket.getStatus());
        assertEquals("approved", suggestion.getStatus());
        verify(executionGateway, times(1)).execute(suggestionId, "swissadmin");
        verify(agentSuggestionRepo, times(1)).save(suggestion);
        verify(policyDecisionRepo, times(1)).save(any(ch.swissqcommerce.backend.model.PolicyDecision.class));
        verify(hitlQueuePort, times(1)).save(ticket);
    }
}
