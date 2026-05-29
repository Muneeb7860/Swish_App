package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.core.model.RiderAcademyCertificate;
import ch.swissqcommerce.backend.domain.enrollment.core.service.RiderServiceImpl;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
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

    @Mock private EnrollmentOutPort outPort;

    @InjectMocks private RiderServiceImpl riderService;

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

        when(outPort.findOrderById(1)).thenReturn(Optional.of(order));

        Map<String, Object> result = riderService.confirmDelivery(1);

        assertEquals("delivered", order.getStatus());
        assertEquals(95, rider.getTrustScore());
        assertEquals(93, customer.getTrustScore());
        assertEquals(3, customer.getConsecutiveOrdersCompleted());
        assertFalse(customer.getIsOnProbation());
        
        verify(outPort, times(1)).saveOrder(order);
        verify(outPort, times(1)).saveRider(rider);
        verify(outPort, times(1)).saveCustomer(customer);
        verify(outPort, times(1)).saveTrustLedger(any(SecurityTrustLedger.class));
    }

    @Test
    public void testCompleteAcademyCourse() {
        Rider rider = new Rider();
        rider.setRiderId("R1");
        rider.setTrustScore(50);

        when(outPort.findRiderById("R1")).thenReturn(Optional.of(rider));
        
        Map<String, Object> result = riderService.completeAcademyCourse("R1", "COURSE_001");
        
        assertEquals("course_completed", result.get("status"));
        assertEquals(60, result.get("new_trust_score"));
        verify(outPort, times(1)).saveRiderAcademyCertificate(any(RiderAcademyCertificate.class));
        verify(outPort, times(1)).saveTrustLedger(any(SecurityTrustLedger.class));
    }
}
