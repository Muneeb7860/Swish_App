package ch.swissqcommerce.backend.domain.billing.adapter.in.web;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.billing.core.model.Invoice;
import ch.swissqcommerce.backend.domain.billing.port.in.BillingUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Billing engine API (BRD FR-06). Administrator-only: subscription and invoice
 * operations are operator functions, gated at the method layer in addition to
 * the gateway/OPA URL policy.
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Flat-tier hub subscriptions and invoicing")
public class BillingController {

    private final BillingUseCase billing;

    public record SubscribeRequest(String storeId, BillingTier tier) {}
    public record TierRequest(BillingTier tier) {}
    public record InvoicePeriodRequest(LocalDate periodStart, LocalDate periodEnd) {}

    @Operation(summary = "Subscribe a hub to a flat-tier billing plan")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/accounts")
    public ResponseEntity<?> subscribe(@RequestBody SubscribeRequest req) {
        try {
            BillingAccount account = billing.subscribe(req.storeId(), req.tier());
            return ResponseEntity.status(201).body(account);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Change a billing account's tier")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/accounts/{accountId}/tier")
    public ResponseEntity<?> changeTier(@PathVariable String accountId, @RequestBody TierRequest req) {
        try {
            return ResponseEntity.ok(billing.changeTier(accountId, req.tier()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Get a billing account")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<?> getAccount(@PathVariable String accountId) {
        return billing.getAccount(accountId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Billing account not found")));
    }

    @Operation(summary = "Generate a flat-tier invoice for a billing period")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/accounts/{accountId}/invoices")
    public ResponseEntity<?> generateInvoice(@PathVariable String accountId,
                                             @RequestBody InvoicePeriodRequest req) {
        try {
            Invoice invoice = billing.generateInvoice(accountId, req.periodStart(), req.periodEnd());
            return ResponseEntity.status(201).body(invoice);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "List invoices for a billing account")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/accounts/{accountId}/invoices")
    public ResponseEntity<List<Invoice>> getInvoices(@PathVariable String accountId) {
        return ResponseEntity.ok(billing.getInvoices(accountId));
    }

    @Operation(summary = "Mark an invoice paid")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/invoices/{invoiceId}/pay")
    public ResponseEntity<?> payInvoice(@PathVariable String invoiceId) {
        try {
            return ResponseEntity.ok(billing.markInvoicePaid(invoiceId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
