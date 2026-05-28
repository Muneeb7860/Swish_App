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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RiderServiceTest {

    @Mock private RiderRepository riderRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;
    @Mock private TelemetryService telemetryService;
    @Mock private RiderAcademyCertificateRepository certificateRepository;

    @InjectMocks private RiderService riderService;

    @Test
    public void testConfirmDelivery_Success() {
        Rider rider = new Rider();
        rider.setTrustScore(90);

        Customer customer = new Customer();
        customer.setTrustScore(90);
        customer.setConsecutiveOrdersCompleted(2);
        customer.setIsOnProbation(true);

        Order order = new Order();
        order.setStatus("shipping");
        order.setRider(rider);
        order.setCustomer(customer);

        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        Map<String, Object> result = riderService.confirmDelivery(1);

        assertEquals("delivered", order.getStatus());
        assertEquals(95, rider.getTrustScore());
        assertEquals(93, customer.getTrustScore());
        assertEquals(3, customer.getConsecutiveOrdersCompleted());
        assertFalse(customer.getIsOnProbation());
        
        verify(riderRepository, times(1)).save(rider);
        verify(customerRepository, times(1)).save(customer);
        verify(trustLedgerRepository, times(1)).save(any(SecurityTrustLedger.class));
    }

    @Test
    public void testCompleteAcademyCourse() {
        Rider rider = new Rider();
        rider.setRiderId("R1");
        rider.setTrustScore(50);

        when(riderRepository.findById("R1")).thenReturn(Optional.of(rider));
        
        Map<String, Object> result = riderService.completeAcademyCourse("R1", "COURSE_001");
        
        assertEquals("course_completed", result.get("status"));
        assertEquals(60, result.get("new_trust_score"));
        verify(certificateRepository, times(1)).save(any(RiderAcademyCertificate.class));
        verify(trustLedgerRepository, times(1)).save(any(SecurityTrustLedger.class));
    }
}
