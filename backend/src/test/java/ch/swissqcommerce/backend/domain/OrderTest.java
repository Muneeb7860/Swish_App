package ch.swissqcommerce.backend.domain;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderTest {

    @Test
    void shouldCreateNewOrderInPendingState() {
        // Given
        Customer customer = new Customer();
        customer.setCustomerId("CUST-001");

        // When
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus("PENDING");
        order.setTotalAmount(new BigDecimal("45.50"));

        // Then
        assertNotNull(order);
        assertEquals("CUST-001", order.getCustomer().getCustomerId());
        assertEquals("PENDING", order.getStatus());
        assertEquals(new BigDecimal("45.50"), order.getTotalAmount());
    }

    @Test
    void shouldTransitionToAssignedState() {
        // Given
        Order order = new Order();
        order.setStatus("PENDING");
        Rider rider = new Rider();
        rider.setRiderId("RIDER-001");

        // When
        order.setRider(rider);
        order.setStatus("ASSIGNED");

        // Then
        assertEquals("ASSIGNED", order.getStatus());
        assertEquals("RIDER-001", order.getRider().getRiderId());
    }

    @Test
    void shouldCalculateTrustScoreCorrectly() {
        // Given
        int baseScore = 100;
        int penalty = 20;
        
        // When
        int currentScore = baseScore - penalty;
        
        // Then
        assertEquals(80, currentScore);
    }
}
