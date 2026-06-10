package ch.swissqcommerce.backend.domain.billing.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A billable hub subscription. {@code storeId} is the active hub (dark store). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingAccount {
    private String accountId;
    private String storeId;
    private BillingTier tier;
    /** ACTIVE, SUSPENDED, or CANCELLED. */
    private String status;
}
