package ch.swissqcommerce.backend.domain.customer.adapter.in.web;

import ch.swissqcommerce.backend.domain.customer.port.in.CustomerUseCase;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Customer",
        description = "Customer catalog, orders, ledger, and GDPR profile operations")
public class CustomerController {

    private final CustomerUseCase customerUseCase;
    private final OrderUseCase orderUseCase;
    private final LedgerUseCase ledgerUseCase;

    // ---- Catalog ----------------------------------------------------------------

    @Operation(
            summary = "Browse product catalog",
            description =
                    "Returns available products for the customer's zone. Optionally filter by"
                            + " category or search term.")
    @GetMapping("/catalog")
    public ResponseEntity<List<Map<String, Object>>> getCatalog(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String cat = category == null ? null : category.trim().toLowerCase();
        String term = search == null ? null : search.trim().toLowerCase();
        List<Map<String, Object>> catalog =
                orderUseCase.browseCatalog().stream()
                        .filter(
                                inv ->
                                        cat == null
                                                || (inv.getCategory() != null
                                                        && inv.getCategory()
                                                                .toLowerCase()
                                                                .equals(cat)))
                        .filter(
                                inv ->
                                        term == null
                                                || (inv.getName() != null
                                                        && inv.getName()
                                                                .toLowerCase()
                                                                .contains(term)))
                        .skip((long) Math.max(0, page) * Math.max(1, size))
                        .limit(Math.max(1, size))
                        .map(
                                inv -> {
                                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                                    m.put("item_id", inv.getItemId());
                                    m.put("name", inv.getName());
                                    m.put("price", inv.getPrice());
                                    m.put("stock", inv.getStock());
                                    m.put("category", inv.getCategory());
                                    m.put("emoji", inv.getEmoji());
                                    m.put("perishable", inv.getPerishable());
                                    return m;
                                })
                        .toList();
        return ResponseEntity.ok(catalog);
    }

    // ---- Orders -----------------------------------------------------------------

    @Data
    public static class CheckoutRequest {
        // Optional in request body, defaulted from JWT principal if blank
        private String customerId;
        @NotNull private List<OrderUseCase.CartItem> items;

        @JsonProperty("payment_method")
        @JsonAlias("paymentMethod")
        @NotBlank
        private String paymentMethod;

        @JsonProperty("tip_amount")
        @JsonAlias("tipAmount")
        private BigDecimal tip;

        @JsonProperty("bags_returned")
        @JsonAlias("bagsReturned")
        private Integer bagsReturned;

        @JsonProperty("idempotency_key")
        @JsonAlias("idempotencyKey")
        private String idempotencyKey;
    }

    @Data
    public static class RefundRequest {
        @JsonProperty("claim_reason")
        @JsonAlias("claimReason")
        @NotBlank
        private String claimReason;

        @JsonProperty("customer_latitude")
        @JsonAlias("customerLatitude")
        @NotNull
        private BigDecimal customerLatitude;

        @JsonProperty("customer_longitude")
        @JsonAlias("customerLongitude")
        @NotNull
        private BigDecimal customerLongitude;

        @JsonProperty("photo_exif_timestamp")
        @JsonAlias("photoExifTimestamp")
        private String photoExifTimestamp;
    }

    /** Asserts the authenticated principal owns the given customerId, or is an admin. */
    private void assertOwnership(String customerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Unauthorized.");
        }
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !customerId.equalsIgnoreCase(auth.getName())) {
            throw new AccessDeniedException("Access denied: you may only access your own data.");
        }
    }

    @Operation(
            summary = "Place a new order (checkout)",
            description = "Idempotency-Key header prevents duplicate orders on retry.")
    @PostMapping("/orders")
    public ResponseEntity<?> placeOrder(
            @Valid @RequestBody CheckoutRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (req.getCustomerId() == null || req.getCustomerId().isBlank()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                req.setCustomerId(auth.getName());
            }
        }
        assertOwnership(req.getCustomerId());
        Order order =
                orderUseCase.checkout(
                        req.getCustomerId(),
                        req.getItems(),
                        req.getPaymentMethod(),
                        req.getTip() != null ? req.getTip() : BigDecimal.ZERO,
                        req.getBagsReturned() != null ? req.getBagsReturned() : 0,
                        idempotencyKey != null ? idempotencyKey : req.getIdempotencyKey());
        return ResponseEntity.status(201).body(order);
    }

    @Operation(summary = "List orders for a customer")
    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(
            @Parameter(description = "Customer ID") @RequestParam(required = false)
                    String customerId) {
        if (customerId == null || customerId.isBlank()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                customerId = auth.getName();
            }
        }
        assertOwnership(customerId);
        return ResponseEntity.ok(orderUseCase.getCustomerOrders(customerId));
    }

    @Operation(
            summary = "Request a refund for an order",
            description =
                    "Validates customer location against delivery address before approving. "
                            + "The authenticated caller must own the order.")
    @PostMapping("/orders/{id}/refund")
    @Transactional
    public ResponseEntity<?> requestRefund(
            @PathVariable Integer id, @Valid @RequestBody RefundRequest req) {
        // Resolve the order first so we can assert ownership before mutating state.
        Order order = orderUseCase.getOrderById(id);
        assertOwnership(order.getCustomer().getCustomerId());
        Map<String, Object> result =
                orderUseCase.requestRefund(
                        id,
                        req.getClaimReason(),
                        req.getCustomerLatitude(),
                        req.getCustomerLongitude());
        Integer statusCode = (Integer) result.getOrDefault("httpStatus", 200);
        return ResponseEntity.status(statusCode).body(result);
    }

    // ---- Ledger -----------------------------------------------------------------

    @Operation(
            summary = "Get customer double-entry ledger",
            description =
                    "Returns all ledger lines (wallet credits/debits, reward cashback, refunds) for"
                            + " the customer.")
    @GetMapping("/ledger")
    public ResponseEntity<?> getLedger(@RequestParam(required = false) String customerId) {
        if (customerId == null || customerId.isBlank()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                customerId = auth.getName();
            }
        }
        assertOwnership(customerId);
        return ResponseEntity.ok(ledgerUseCase.getCustomerLedger(customerId));
    }

    // ---- GDPR Profile -----------------------------------------------------------

    @Operation(
            summary = "GDPR right-to-erasure — permanently purge customer profile",
            description =
                    "Irreversible. Deletes PII, order history, wallet, and loyalty data. Admin or"
                            + " self only.")
    @PostMapping("/profile/purge")
    @Transactional
    public ResponseEntity<?> purgeProfile(@RequestParam(required = false) String customerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized."));
        }
        if (customerId == null || customerId.isBlank()) {
            customerId = auth.getName();
        }
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!customerId.equalsIgnoreCase(auth.getName()) && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied."));
        }
        return ResponseEntity.ok(customerUseCase.purgeProfile(customerId));
    }
}
