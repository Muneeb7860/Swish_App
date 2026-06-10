package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.core.model.RiderAcademyCertificate;
import ch.swissqcommerce.backend.domain.enrollment.core.model.OnboardingApplication;
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
    public void testConfirmDelivery_SuccessWithPin() {
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
        order.setDeliveryPin("1234");

        when(outPort.findOrderById(1)).thenReturn(Optional.of(order));

        Map<String, Object> result = riderService.confirmDelivery(1, "1234", null);

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
    public void testConfirmDelivery_SuccessWithPhotoFallback() {
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
        order.setDeliveryPin("1234");

        when(outPort.findOrderById(1)).thenReturn(Optional.of(order));

        Map<String, Object> result = riderService.confirmDelivery(1, "9999", "http://proof.photo/url");

        assertEquals("delivered", order.getStatus());
        assertEquals("http://proof.photo/url", order.getProofOfDeliveryPhotoUrl());
        
        verify(outPort, times(1)).saveOrder(order);
    }

    @Test
    public void testConfirmDelivery_FailureMismatchedPinAndNoPhoto() {
        Order order = new Order();
        order.setStatus("shipping");
        order.setDeliveryPin("1234");

        when(outPort.findOrderById(1)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> {
            riderService.confirmDelivery(1, "9999", null);
        });
    }

    @Test
    public void testRejectDelivery_Success() {
        Customer customer = new Customer();
        customer.setWalletBalance(new java.math.BigDecimal("50.00"));

        Order order = new Order();
        order.setStatus("shipping");
        order.setCustomer(customer);
        order.setTotalAmount(new java.math.BigDecimal("25.50"));

        when(outPort.findOrderById(1)).thenReturn(Optional.of(order));

        Map<String, Object> result = riderService.rejectDelivery(1, "Damaged perishables", "http://reject.photo/url");

        assertEquals("rejected_at_door", order.getStatus());
        assertEquals("Damaged perishables", order.getRejectionReason());
        assertEquals("http://reject.photo/url", order.getRejectionPhotoUrl());
        assertEquals(new java.math.BigDecimal("75.50"), customer.getWalletBalance());

        verify(outPort, times(1)).saveOrder(order);
        verify(outPort, times(1)).saveCustomer(customer);
        verify(outPort, times(1)).cleanupOrderTelemetry(1);
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

    @Test
    public void testApproveOnboarding_Success() {
        OnboardingApplication app = OnboardingApplication.builder()
                .applicationId("APP1")
                .name("Rider One")
                .applicantType("rider")
                .approvalOps(false)
                .approvalCompliance(false)
                .approvalAdmin(false)
                .build();

        when(outPort.findOnboardingApplicationById("APP1")).thenReturn(Optional.of(app));

        Map<String, Object> result = riderService.approveOnboarding("APP1", "ops");

        assertEquals("gate_approved", result.get("status"));
        assertTrue(app.getApprovalOps());
        assertFalse(app.getApprovalCompliance());
        assertFalse(app.getApprovalAdmin());
        assertFalse((Boolean) result.get("fullyApproved"));

        verify(outPort, times(1)).saveOnboardingApplication(app);
    }

    @Test
    public void testApproveOnboarding_FullyApproved() {
        OnboardingApplication app = OnboardingApplication.builder()
                .applicationId("APP1")
                .name("Rider One")
                .applicantType("rider")
                .approvalOps(true)
                .approvalCompliance(true)
                .approvalAdmin(false)
                .build();

        Rider rider = Rider.builder()
                .riderId("R1")
                .fullName("Rider One")
                .onboardingStatus("pending_review")
                .build();

        when(outPort.findOnboardingApplicationById("APP1")).thenReturn(Optional.of(app));
        when(outPort.findRiderByFullName("Rider One")).thenReturn(Optional.of(rider));

        Map<String, Object> result = riderService.approveOnboarding("APP1", "admin");

        assertEquals("fully_approved", result.get("status"));
        assertTrue(app.getApprovalOps());
        assertTrue(app.getApprovalCompliance());
        assertTrue(app.getApprovalAdmin());
        assertTrue((Boolean) result.get("fullyApproved"));
        assertEquals("approved", rider.getOnboardingStatus());

        verify(outPort, times(1)).saveOnboardingApplication(app);
        verify(outPort, times(1)).saveRider(rider);
    }
}
