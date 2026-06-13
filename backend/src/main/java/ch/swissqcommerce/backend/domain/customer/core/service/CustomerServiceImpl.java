package ch.swissqcommerce.backend.domain.customer.core.service;

import ch.swissqcommerce.backend.domain.customer.port.in.CustomerUseCase;
import ch.swissqcommerce.backend.domain.customer.port.out.CustomerPort;
import ch.swissqcommerce.backend.model.Customer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class CustomerServiceImpl implements CustomerUseCase {

    private final CustomerPort customerPort;

    public CustomerServiceImpl(CustomerPort customerPort) {
        this.customerPort = customerPort;
    }

    @Override
    public Map<String, Object> purgeProfile(String customerId) {
        Customer customer =
                customerPort
                        .findCustomerById(customerId)
                        .orElseThrow(() -> new NoSuchElementException("Customer not found."));

        // GDPR F04 Right to Erasure anonymization
        customer.setFullName("ANONYMIZED-GDPR-CUST");
        customer.setEmail(null);
        customer.setIsAnonymized(true);
        customer.setIsOnProbation(true);
        customer.setConsecutiveOrdersCompleted(0);
        customer.setTrustScore(75); // Reset to probationary trust score of 75

        // Cascade delete saved addresses and cards
        if (customer.getAddresses() != null) {
            customer.getAddresses().clear();
        }
        if (customer.getPaymentCards() != null) {
            customer.getPaymentCards().clear();
        }

        Customer savedCust = customerPort.saveCustomer(customer);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "purged");
        result.put("probationary_trust_score", savedCust.getTrustScore());
        return result;
    }
}
