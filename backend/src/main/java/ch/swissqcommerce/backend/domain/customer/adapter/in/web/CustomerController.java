package ch.swissqcommerce.backend.domain.customer.adapter.in.web;

import ch.swissqcommerce.backend.domain.customer.port.in.CustomerUseCase;
import ch.swissqcommerce.backend.domain.transaction.core.model.LedgerLine;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "Customer catalog, orders, ledger, and GDPR profile operations")
public class CustomerController {

    private final CustomerUseCase customerUseCase;
    private final OrderUseCase orderUseCase;
    private final LedgerUseCase ledgerUseCase;

    // ---- Catalog ----------------------------------------------------------------

    @Operation(summary = "Browse product catalog",
               description = "Returns available products for the customer's zone. Optionally filter by category or search term.")
    @GetMapping("/catalog")
    public ResponseEntity<List<Map<String, Object>>> getCatalog(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Delegated to catalog/inventory service in full implementation.
        // Stub returns empty list; replace with CatalogUseCase once wired.
        return ResponseEntity.ok(List.of());
    }

    // ---- Orders -----------------------------------------------------------------

    @Data
    public static class CheckoutRequest {
        @NotBlank private String customerId;
        @NotNull  private List<OrderUseCase.CartItem> items;
        @NotBlank private String paymentMethod;
        private BigDecimal tip;
        private Integer bagsReturned;
        private String idempotencyKey;
    }

    @Data
    public static class RefundRequest {
        @NotBlank private String claimReason;
        @NotNull  private BigDecimal customerLatitude;
        @NotNull  private BigDecimal customerLongitude;
    }

    /** Asserts the authenticated principal owns the given customerId, or is an admin. */
    private void assertOwnership(String customerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Unauthorized.");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !customerId.equalsIgnoreCase(auth.getName())) {
            throw new AccessDeniedException("Access denied: you may only access your own data.");
        }
    }

    @Operation(summary = "Place a new order (checkout)",
               description = "Idempotency-Key header prevents duplicate orders on retry.")
    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<?> placeOrder(
            @Valid @RequestBody CheckoutRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        assertOwnership(req.getCustomerId());
        try {
            Order order = orderUseCase.checkout(
                    req.getCustomerId(),
                    req.getItems(),
                    req.getPaymentMethod(),
                    req.getTip() != null ? req.getTip() : BigDecimal.ZERO,
                    req.getBagsReturned() != null ? req.getBagsReturned() : 0,
                    idempotencyKey != null ? idempotencyKey : req.getIdempotencyKey());
            return ResponseEntity.status(201).body(order);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "List orders for a customer")
    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(
            @Parameter(description = "Customer ID") @RequestParam String customerId) {
        assertOwnership(customerId);
        return ResponseEntity.ok(orderUseCase.getCustomerOrders(customerId));
    }

    @Operation(summary = "Request a refund for an order",
               description = "Validates customer location against delivery address before approving. " +
                             "The authenticated caller must own the order.")
    @PostMapping("/orders/{id}/refund")
    @Transactional
    public ResponseEntity<?> requestRefund(
            @PathVariable Integer id,
            @Valid @RequestBody RefundRequest req) {
        // Resolve the order first so we can assert ownership before mutating state.
        try {
            Order order = orderUseCase.getOrderById(id);
            assertOwnership(order.getCustomer().getCustomerId());
            Map<String, Object> result = orderUseCase.requestRefund(id, req.getClaimReason(),
                    req.getCustomerLatitude(), req.getCustomerLongitude());
            Integer statusCode = (Integer) result.getOrDefault("httpStatus", 200);
            return ResponseEntity.status(statusCode).body(result);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    // ---- Ledger -----------------------------------------------------------------

    @Operation(summary = "Get customer double-entry ledger",
               description = "Returns all ledger lines (wallet credits/debits, reward cashback, refunds) for the customer.")
    @GetMapping("/ledger")
    public ResponseEntity<?> getLedger(
            @RequestParam String customerId) {
        assertOwnership(customerId);
        return ResponseEntity.ok(ledgerUseCase.getCustomerLedger(customerId));
    }

    // ---- GDPR Profile -----------------------------------------------------------

    @Operation(summary = "GDPR right-to-erasure — permanently purge customer profile",
               description = "Irreversible. Deletes PII, order history, wallet, and loyalty data. Admin or self only.")
    @PostMapping("/profile/purge")
    @Transactional
    public ResponseEntity<?> purgeProfile(@RequestParam String customerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized."));
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!customerId.equalsIgnoreCase(auth.getName()) && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied."));
        }
        return ResponseEntity.ok(customerUseCase.purgeProfile(customerId));
    }
}
