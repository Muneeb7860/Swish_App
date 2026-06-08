package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.customer.core.service.CustomerServiceImpl;
import ch.swissqcommerce.backend.domain.customer.port.out.CustomerPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.CustomerAddress;
import ch.swissqcommerce.backend.model.CustomerPaymentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomerServiceTest {

    @Mock
    private CustomerPort customerPort;

    @InjectMocks
    private CustomerServiceImpl customerService;

    @Test
    public void testPurgeProfile_Success() {
        Customer customer = new Customer();
        customer.setCustomerId("C1");
        customer.setFullName("Luke Skywalker");
        customer.setEmail("luke@tatooine.org");
        customer.setTrustScore(100);
        customer.setIsAnonymized(false);
        customer.setIsOnProbation(false);
        customer.setConsecutiveOrdersCompleted(5);

        List<CustomerAddress> addresses = new ArrayList<>();
        addresses.add(new CustomerAddress());
        customer.setAddresses(addresses);

        List<CustomerPaymentCard> cards = new ArrayList<>();
        cards.add(new CustomerPaymentCard());
        customer.setPaymentCards(cards);

        when(customerPort.findCustomerById("C1")).thenReturn(Optional.of(customer));
        when(customerPort.saveCustomer(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = customerService.purgeProfile("C1");

        assertEquals("purged", result.get("status"));
        assertEquals(75, result.get("probationary_trust_score"));

        assertEquals("ANONYMIZED-GDPR-CUST", customer.getFullName());
        assertNull(customer.getEmail());
        assertTrue(customer.getIsAnonymized());
        assertTrue(customer.getIsOnProbation());
        assertEquals(0, customer.getConsecutiveOrdersCompleted());
        assertEquals(75, customer.getTrustScore());
        assertTrue(customer.getAddresses().isEmpty());
        assertTrue(customer.getPaymentCards().isEmpty());

        verify(customerPort, times(1)).saveCustomer(customer);
    }
}
