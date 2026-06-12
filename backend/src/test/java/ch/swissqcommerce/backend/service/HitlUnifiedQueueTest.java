package ch.swissqcommerce.backend.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 8: unified HITL queue + composite-id resolution in {@link GovernanceServiceImpl}.
 * Proves the supervisor sees BOTH B2B procurement overrides and agent escalations in
 * one list, and that approve/reject routes back to the correct source queue.
 */
@ExtendWith(MockitoExtension.class)
class HitlUnifiedQueueTest {

    @Mock private ProcurementApprovalPort approvalsPort;
    @Mock private B2BRestockOrderPort restockOrderPort;
    @Mock private TelemetryPort telemetryPort;
    @Mock private HitlQueuePort hitlQueuePort;

    private GovernanceServiceImpl service;

    @BeforeEach
    void setUp() {
        // null MeterRegistry → constructor skips the gauge (guarded).
        service = new GovernanceServiceImpl(approvalsPort, restockOrderPort, telemetryPort, hitlQueuePort, null);
    }

    private ProcurementApproval approval(int id, String status) {
        return ProcurementApproval.builder()
                .id(id).restockOrderId(42).wholesalerId("W1")
                .amount(new BigDecimal("6000.00")).status(status).build();
    }

    private HitlQueue agentTicket(String ticketId, String status) {
        return HitlQueue.builder()
                .ticketId(ticketId).type("agent_escalation")
                .description("Low confidence escalation")
                .amount(new BigDecimal("0.00")).status(status).build();
    }

    @Test
    void getPendingHitlItems_mergesBothSourcesAndFiltersResolved() {
        when(approvalsPort.findAll()).thenReturn(List.of(approval(1, "PENDING"), approval(2, "APPROVED")));
        when(hitlQueuePort.findByStatus("pending")).thenReturn(List.of(agentTicket("HITL-ABC", "pending")));

        List<HitlItem> items = service.getPendingHitlItems();

        assertEquals(2, items.size(), "one pending procurement + one pending agent escalation");
        HitlItem pa = items.stream().filter(i -> "B2B_PROCUREMENT".equals(i.getSource())).findFirst().orElseThrow();
        HitlItem aq = items.stream().filter(i -> "AGENT_ESCALATION".equals(i.getSource())).findFirst().orElseThrow();
        assertEquals("PA-1", pa.getId());
        assertEquals("AQ-HITL-ABC", aq.getId());
        assertEquals("PENDING", pa.getStatus());
        assertEquals("PENDING", aq.getStatus());
        assertEquals("pricing_review".equals(aq.getType()) ? "pricing_review" : "agent_escalation", aq.getType());
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
        when(hitlQueuePort.findByTicketId("HITL-ABC")).thenReturn(Optional.of(agentTicket("HITL-ABC", "approved")));

        assertThrows(TicketAlreadyResolvedException.class, () ->
                service.resolveHitlItem("AQ-HITL-ABC", true, "swissadmin", "again"));
        verify(hitlQueuePort, never()).save(any());
    }

    @Test
    void resolveAgentEscalation_missingTicket_throwsNotFound() {
        when(hitlQueuePort.findByTicketId("HITL-NONE")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                service.resolveHitlItem("AQ-HITL-NONE", false, "swissadmin", "x"));
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
        assertThrows(IllegalArgumentException.class, () ->
                service.resolveHitlItem("ZZ-7", true, "swissadmin", "x"));
    }
}
