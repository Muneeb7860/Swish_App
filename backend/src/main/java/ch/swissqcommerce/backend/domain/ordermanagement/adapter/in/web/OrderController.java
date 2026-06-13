package ch.swissqcommerce.backend.domain.ordermanagement.adapter.in.web;

import ch.swissqcommerce.backend.domain.ordermanagement.port.in.OrderManagementUseCase;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController("orderManagementOrderController")
@RequestMapping("/api/v1/orders/saga")
@RequiredArgsConstructor
public class OrderController {
    private final OrderManagementUseCase orderUseCase;

    /**
     * Asserts the authenticated principal owns {@code customerId}, or is an ADMIN. Mirrors the
     * ownership guard used across the customer/payment domains so a caller cannot open an order
     * saga on another customer's behalf (IDOR).
     */
    private void assertOwnership(String customerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Unauthorized.");
        }
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !customerId.equalsIgnoreCase(auth.getName())) {
            throw new AccessDeniedException(
                    "Access denied: you may only place orders for your own account.");
        }
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(
            @RequestParam String orderId, @RequestParam String customerId) {
        if (orderId == null || orderId.isBlank() || customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "orderId and customerId are required."));
        }
        try {
            // Authorization must precede the side-effecting saga trigger.
            assertOwnership(customerId);
            orderUseCase.handleOrderCreated(orderId, customerId);
            return ResponseEntity.accepted().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
