package ch.swissqcommerce.backend.domain.auth.adapter.out.messaging;

import ch.swissqcommerce.backend.domain.auth.port.out.AuthEventPublisherPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthEventPublisherAdapter implements AuthEventPublisherPort {

    @Autowired
    private CustomerRepository customerRepository;

    @Override
    public void publishUserRegisteredEvent(String userId, String email) {
        if (userId == null || email == null) return;

        // Check if customer already exists (to be idempotent)
        if (customerRepository.findById(userId).isPresent()) {
            return;
        }

        // Extract part of email as name
        String name = email.split("@")[0];
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + (name.length() > 1 ? name.substring(1) : "");
        } else {
            name = "Swish Customer";
        }

        Customer customer = Customer.builder()
                .customerId(userId)
                .fullName(name)
                .email(email)
                .hashedEmail(hashEmail(email))
                .walletBalance(new BigDecimal("100.00"))
                .loyaltyPoints(0)
                .vipStatus(false)
                .trustScore(100)
                .isAnonymized(false)
                .isOnProbation(false)
                .consecutiveOrdersCompleted(0)
                .build();

        customerRepository.save(customer);
    }

    private String hashEmail(String email) {
        if (email == null) return "";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.trim().toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "hash-" + email.hashCode();
        }
    }
}
