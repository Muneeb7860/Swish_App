package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CustomerRepository customerRepository;


    @PostMapping("/profile/purge")
    @Transactional
    public ResponseEntity<?> purgeProfile(@RequestParam String customerId) {
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized."));
        }
        String principalName = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!customerId.equalsIgnoreCase(principalName) && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied. Cannot purge another customer's profile."));
        }

        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Customer not found."));
        }
        Customer customer = customerOpt.get();

        // GDPR F04 Right to Erasure anonymization
        customer.setFullName("ANONYMIZED-GDPR-CUST");
        customer.setEmail(null);
        customer.setIsAnonymized(true);
        customer.setIsOnProbation(true);
        customer.setConsecutiveOrdersCompleted(0);
        customer.setTrustScore(75); // Reset to probationary trust score of 75

        // Cascade delete saved addresses and cards
        customer.getAddresses().clear();
        customer.getPaymentCards().clear();

        Customer savedCust = customerRepository.save(customer);

        return ResponseEntity.ok(Map.of(
            "status", "purged",
            "probationary_trust_score", savedCust.getTrustScore()
        ));
    }

    private static class Map {
        public static java.util.Map<String, Object> of(String k1, Object v1) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put(k1, v1);
            return map;
        }
        public static java.util.Map<String, Object> of(String k1, Object v1, String k2, Object v2) {
            java.util.Map<String, Object> map = of(k1, v1);
            map.put(k2, v2);
            return map;
        }
    }
}
