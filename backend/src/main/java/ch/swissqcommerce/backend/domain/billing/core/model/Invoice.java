package ch.swissqcommerce.backend.domain.billing.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A flat-tier invoice for one billing period of a {@link BillingAccount}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {
    private String invoiceId;
    private String accountId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private String currency;
    /** DRAFT, ISSUED, PAID, or VOID. */
    private String status;
    private OffsetDateTime issuedAt;
    private OffsetDateTime paidAt;
}
