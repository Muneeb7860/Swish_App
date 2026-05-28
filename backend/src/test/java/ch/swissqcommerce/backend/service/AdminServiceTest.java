package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock private ChaosFaultLogRepository chaosFaultLogRepository;
    @Mock private OnboardingApplicationRepository onboardingRepository;
    @Mock private HitlQueueRepository hitlQueueRepository;
    @Mock private RiderRepository riderRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private LedgerService ledgerService;
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
        verify(ledgerService, times(1)).recordTransaction(anyString(), anyString(), anyList());
        assertEquals("approved", res.get("status"));
    }
}
