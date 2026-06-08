package ch.swissqcommerce.backend.domain.payment.adapter.in.web;

import ch.swissqcommerce.backend.domain.payment.core.model.Money;
import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.core.model.TransactionRecord;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentProcessingUseCase;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment authorisation, capture, refund, and compensation")
public class PaymentController {

    private final PaymentProcessingUseCase paymentProcessingUseCase;
    private final PaymentUseCase paymentUseCase;

    /** Asserts the authenticated principal owns the given customerId, or is ADMIN. */
    private void assertPaymentOwnership(String paymentCustomerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Unauthorized.");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !paymentCustomerId.equalsIgnoreCase(auth.getName())) {
            throw new AccessDeniedException("Access denied: payment does not belong to you.");
        }
    }

    @Data
    public static class ChargeRequest {
        @NotBlank(message = "Order ID is required")
        private String orderId;
        @NotNull @Positive
        private BigDecimal amount;
        @NotBlank
        private String currency;
    }

    @Data
    public static class AuthorizeRequest {
        @NotNull
        private Integer orderId;
        @NotBlank
        private String customerId;
        @NotNull @Positive
        private BigDecimal amount;
        @NotBlank
        private String paymentMethod;
        private String idempotencyKey;
    }

    // ---- Authorize (POST /api/payments) ----------------------------------------

    @Operation(summary = "Authorise a payment",
               description = "Reserves funds against an order. Returns AUTHORIZED status. " +
                             "Call capture to settle. Idempotency-Key prevents duplicates.")
    @PostMapping
    public ResponseEntity<?> authorize(
            @Valid @RequestBody AuthorizeRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            Payment payment = paymentUseCase.authorizePayment(
                    req.getOrderId(), req.getCustomerId(), req.getAmount(), req.getPaymentMethod(),
                    idempotencyKey);
            return ResponseEntity.status(201).body(payment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---- List (GET /api/payments) -----------------------------------------------

    @Operation(summary = "List payments for a customer")
    @GetMapping
    public ResponseEntity<List<Payment>> listPayments(@RequestParam String customerId) {
        return ResponseEntity.ok(paymentUseCase.getPaymentsByCustomer(customerId));
    }

    // ---- Get by ID (GET /api/payments/{id}) -------------------------------------

    @Operation(summary = "Get a single payment by ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getPayment(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(paymentUseCase.getPayment(id));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    // ---- Capture (POST /api/payments/{id}/capture) ------------------------------

    @Operation(summary = "Capture an authorised payment",
               description = "Settles a previously AUTHORIZED payment. Moves status to CAPTURED " +
                             "and triggers the payment.captured + payment.notification outbox events.")
    @PostMapping("/{id}/capture")
    public ResponseEntity<?> capture(@PathVariable Integer id) {
        try {
            Payment payment = paymentUseCase.getPayment(id);
            assertPaymentOwnership(payment.getCustomerId());
            return ResponseEntity.ok(paymentUseCase.capturePayment(id));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---- Compensate (POST /api/payments/{id}/compensate) -------------------------

    @Operation(summary = "Compensate (reverse) a payment",
               description = "Issues a full reversal for fraud, system error, or regulatory compliance. " +
                             "Creates a balancing ledger entry and publishes payment.compensated event.")
    @PostMapping("/{id}/compensate")
    public ResponseEntity<?> compensate(
            @PathVariable Integer id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            Payment payment = paymentUseCase.getPayment(id);
            assertPaymentOwnership(payment.getCustomerId());
            // Compensation delegates to refund logic; a dedicated compensate use-case
            // should be wired once the SAGA coordinator is implemented.
            TransactionRecord tx = paymentProcessingUseCase.processRefund(String.valueOf(id));
            return ResponseEntity.ok(tx);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---- Legacy charge endpoint (POST /api/payments/charge) ---------------------

    @Operation(summary = "Direct charge (legacy)",
               description = "Single-step charge without separate authorize/capture. Prefer the " +
                             "authorize + capture flow for production use.")
    @PostMapping("/charge")
    public ResponseEntity<?> charge(@Valid @RequestBody ChargeRequest request) {
        try {
            Money money = new Money(request.getAmount(), request.getCurrency());
            TransactionRecord tx = paymentProcessingUseCase.processCharge(request.getOrderId(), money);
            return ResponseEntity.ok(tx);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ---- Refund (POST /api/payments/{id}/refund) --------------------------------

    @Operation(summary = "Refund a payment by transaction ID")
    @PostMapping("/{transactionId}/refund")
    public ResponseEntity<?> refund(@PathVariable String transactionId) {
        try {
            // Resolve the payment to check ownership before processing the refund.
            // transactionId may be numeric (paymentId) or an external reference.
            try {
                Integer paymentId = Integer.valueOf(transactionId);
                Payment payment = paymentUseCase.getPayment(paymentId);
                assertPaymentOwnership(payment.getCustomerId());
            } catch (NumberFormatException ignored) {
                // Non-numeric transaction IDs (external references) — ownership check
                // must be enforced downstream when the full transaction store is wired.
            }
            return ResponseEntity.ok(paymentProcessingUseCase.processRefund(transactionId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
