package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.core.service.GovernanceServiceImpl;
import ch.swissqcommerce.backend.domain.governance.port.out.ProcurementApprovalPort;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.exception.TicketAlreadyResolvedException;
import ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the HITL ticket idempotency state machine in
 * {@link GovernanceServiceImpl}. Proves that once a ticket leaves {@code PENDING}
 * a second approve/reject is rejected with {@link TicketAlreadyResolvedException}
 * (→ HTTP 409) and the underlying B2B restock order is NOT re-processed.
 */
@ExtendWith(MockitoExtension.class)
class GovernanceServiceIdempotencyTest {

    @Mock private ProcurementApprovalPort approvalsPort;
    @Mock private B2BRestockOrderPort restockOrderPort;
    @Mock private TelemetryPort telemetryPort;
    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;

    @InjectMocks private GovernanceServiceImpl service;

    private ProcurementApproval pendingTicket;

    @BeforeEach
    void setUp() {
        pendingTicket = ProcurementApproval.builder()
                .id(1)
                .restockOrderId(42)
                .wholesalerId("W1")
                .amount(new BigDecimal("6000.00"))
                .status("PENDING")
                .build();
    }

    private B2BRestockOrder restockOrder() {
        return B2BRestockOrder.builder()
                .restockOrderId(42)
                .status("pending")
                .build();
    }

    @Test
    void firstApproveFulfilsTheRestockOrder() {
        when(approvalsPort.findById(1)).thenReturn(Optional.of(pendingTicket));
        when(restockOrderPort.findById(42)).thenReturn(Optional.of(restockOrder()));

        service.approveOverride(1, "ops-admin", "High demand");

        assertEquals("APPROVED", pendingTicket.getStatus());
        verify(restockOrderPort, times(1)).save(any(B2BRestockOrder.class));
    }

    @Test
    void secondApproveIsRejectedAndDoesNotReprocess() {
        ProcurementApproval alreadyApproved = ProcurementApproval.builder()
                .id(1).restockOrderId(42).status("APPROVED").build();
        when(approvalsPort.findById(1)).thenReturn(Optional.of(alreadyApproved));

        assertThrows(TicketAlreadyResolvedException.class,
                () -> service.approveOverride(1, "ops-admin", "High demand"));

        // No state mutation and no restock-order re-fulfilment on the duplicate call.
        verify(approvalsPort, never()).save(any(ProcurementApproval.class));
        verify(restockOrderPort, never()).findById(any());
        verify(restockOrderPort, never()).save(any(B2BRestockOrder.class));
    }

    @Test
    void rejectOfAlreadyResolvedTicketIsRejected() {
        ProcurementApproval alreadyRejected = ProcurementApproval.builder()
                .id(1).restockOrderId(42).status("REJECTED").build();
        when(approvalsPort.findById(1)).thenReturn(Optional.of(alreadyRejected));

        assertThrows(TicketAlreadyResolvedException.class,
                () -> service.rejectOverride(1, "ops-admin", "Over budget"));

        verify(approvalsPort, never()).save(any(ProcurementApproval.class));
        verify(restockOrderPort, never()).save(any(B2BRestockOrder.class));
    }
}
