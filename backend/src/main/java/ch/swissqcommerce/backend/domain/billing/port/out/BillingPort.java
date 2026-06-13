package ch.swissqcommerce.backend.domain.billing.port.out;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.Invoice;
import java.util.List;
import java.util.Optional;

/** Outbound persistence port for the billing context. */
public interface BillingPort {
    BillingAccount saveAccount(BillingAccount account);

    Optional<BillingAccount> findAccountById(String accountId);

    Invoice saveInvoice(Invoice invoice);

    Optional<Invoice> findInvoiceById(String invoiceId);

    List<Invoice> findInvoicesByAccountId(String accountId);
}
