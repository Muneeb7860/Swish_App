package ch.swissqcommerce.backend.domain.retailer.core.model;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A B2B SaaS tenant onboarded through the 3-gate approval pattern. Reuses {@link BillingTier} so an
 * activated retailer maps straight to a billing account (FR-01 → FR-06).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Retailer {
    private String retailerId;
    private String name;
    private String contactEmail;
    private String storeId;
    private BillingTier tier;

    /** PENDING, ACTIVE, SUSPENDED, or REJECTED. */
    private String status;

    private boolean approvalOps;
    private boolean approvalCompliance;
    private boolean approvalAdmin;

    /** SHA-256 hash of the issued API key; the plaintext is returned only once. */
    private String apiKeyHash;

    private String billingAccountId;
}
