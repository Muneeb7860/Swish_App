package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.OnboardingApplicationRepository;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock private ChaosFaultLogRepository chaosFaultLogRepository;
    @Mock private OnboardingApplicationRepository onboardingRepository;
    @Mock private HitlQueueRepository hitlQueueRepository;
    @Mock private RiderRepository riderRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private LedgerUseCase ledgerService;
    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;

    @InjectMocks private AdminService adminService;

    @Test
    public void testInjectFault_Success() {
        ChaosFaultLog fault = new ChaosFaultLog();
        fault.setFaultType("LATENCY_SPIKE");

        when(chaosFaultLogRepository.save(any())).thenReturn(fault);

        ChaosFaultLog result = adminService.injectFault("LATENCY_SPIKE", "Test");
        assertNotNull(result);
        assertEquals("LATENCY_SPIKE", result.getFaultType());
    }

    @Test
    public void testResolveHitlTicket_ApproveRefund() {
        Customer c = new Customer();
        c.setCustomerId("C-1");

        HitlQueue ticket = new HitlQueue();
        ticket.setStatus("pending");
        ticket.setType("refund_customer");
        ticket.setAmount(new BigDecimal("15.00"));
        ticket.setCustomer(c);

        when(hitlQueueRepository.findById("T-1")).thenReturn(Optional.of(ticket));

        Map<String, Object> res = adminService.resolveHitlTicket("T-1", "approve", "Looks good");

        assertEquals("approved", ticket.getStatus());
        verify(ledgerService, times(2)).recordTransaction(anyString(), anyString(), anyList());
        assertEquals("approved", res.get("status"));
    }
}
