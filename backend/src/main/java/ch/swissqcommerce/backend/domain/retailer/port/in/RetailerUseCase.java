package ch.swissqcommerce.backend.domain.retailer.port.in;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.retailer.core.model.Retailer;
import java.util.List;
import java.util.Optional;

/** Retailer self-service onboarding use cases (BRD FR-01). */
public interface RetailerUseCase {

    /**
     * Result of a gate approval; {@code issuedApiKey} is non-null only on the approval that
     * activates the retailer (returned exactly once).
     */
    record ApprovalResult(Retailer retailer, String issuedApiKey) {}

    Retailer register(String name, String contactEmail, String storeId, BillingTier tier);

    /** Advance one approval gate: "ops", "compliance", or "admin" (sequential). */
    ApprovalResult approveGate(String retailerId, String gate);

    Optional<Retailer> getRetailer(String retailerId);

    /** List retailers in an onboarding status (e.g. "PENDING") — the admin approval queue. */
    List<Retailer> listByStatus(String status);

    /** Resolve an ACTIVE retailer by raw API key (hashed internally). */
    Optional<Retailer> authenticateByApiKey(String apiKey);
}
