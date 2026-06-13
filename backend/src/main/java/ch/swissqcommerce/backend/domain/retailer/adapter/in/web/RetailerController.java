package ch.swissqcommerce.backend.domain.retailer.adapter.in.web;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.retailer.core.model.Retailer;
import ch.swissqcommerce.backend.domain.retailer.port.in.RetailerUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Retailer self-service onboarding API (BRD FR-01). Registration is public (self-signup, creates a
 * PENDING application); gate approvals and reads are administrator-only. The API-key hash is never
 * exposed; the plaintext key is returned exactly once, on the activating approval.
 */
@RestController
@RequestMapping("/api/v1/retailers")
@RequiredArgsConstructor
@Tag(name = "Retailer", description = "B2B retailer self-service onboarding")
public class RetailerController {

    private final RetailerUseCase retailers;

    public record RegisterRequest(
            String name, String contactEmail, String storeId, BillingTier tier) {}

    /** Safe outward view — excludes the API-key hash. */
    public record RetailerView(
            String retailerId,
            String name,
            String contactEmail,
            String storeId,
            BillingTier tier,
            String status,
            boolean approvalOps,
            boolean approvalCompliance,
            boolean approvalAdmin,
            String billingAccountId) {
        static RetailerView of(Retailer r) {
            return new RetailerView(
                    r.getRetailerId(),
                    r.getName(),
                    r.getContactEmail(),
                    r.getStoreId(),
                    r.getTier(),
                    r.getStatus(),
                    r.isApprovalOps(),
                    r.isApprovalCompliance(),
                    r.isApprovalAdmin(),
                    r.getBillingAccountId());
        }
    }

    @Operation(summary = "Self-service retailer registration (public)")
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            Retailer r =
                    retailers.register(req.name(), req.contactEmail(), req.storeId(), req.tier());
            return ResponseEntity.status(201).body(RetailerView.of(r));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Approve one onboarding gate (ops|compliance|admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{retailerId}/gates/{gate}/approve")
    public ResponseEntity<?> approveGate(
            @PathVariable String retailerId, @PathVariable String gate) {
        try {
            RetailerUseCase.ApprovalResult result = retailers.approveGate(retailerId, gate);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("retailer", RetailerView.of(result.retailer()));
            if (result.issuedApiKey() != null) {
                // Returned exactly once — the retailer must store it now.
                body.put("apiKey", result.issuedApiKey());
                body.put(
                        "message",
                        "Retailer activated. Store this API key securely; it will not be shown"
                                + " again.");
            }
            return ResponseEntity.ok(body);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Get a retailer")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{retailerId}")
    public ResponseEntity<?> getRetailer(@PathVariable String retailerId) {
        return retailers
                .getRetailer(retailerId)
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok(RetailerView.of(r)))
                .orElseGet(
                        () ->
                                ResponseEntity.status(404)
                                        .body(Map.of("error", "Retailer not found")));
    }
}
