package ch.swissqcommerce.backend.domain.customer.adapter.in.web;

import ch.swissqcommerce.backend.domain.customer.port.in.CustomerUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private CustomerUseCase customerUseCase;

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

        Map<String, Object> result = customerUseCase.purgeProfile(customerId);
        return ResponseEntity.ok(result);
    }
}
