package ch.swissqcommerce.backend.domain.billing.port.in;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.billing.core.model.Invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Billing engine use cases (BRD FR-06 — flat-tier subscription + invoicing). */
public interface BillingUseCase {
    BillingAccount subscribe(String storeId, BillingTier tier);
    BillingAccount changeTier(String accountId, BillingTier newTier);
    Invoice generateInvoice(String accountId, LocalDate periodStart, LocalDate periodEnd);
    Invoice markInvoicePaid(String invoiceId);
    List<Invoice> getInvoices(String accountId);
    Optional<BillingAccount> getAccount(String accountId);
}
