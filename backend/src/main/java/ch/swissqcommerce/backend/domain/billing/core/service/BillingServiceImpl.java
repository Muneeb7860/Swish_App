package ch.swissqcommerce.backend.domain.billing.core.service;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.billing.core.model.Invoice;
import ch.swissqcommerce.backend.domain.billing.port.in.BillingUseCase;
import ch.swissqcommerce.backend.domain.billing.port.out.BillingPort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingUseCase {

    private final BillingPort port;

    @Override
    @Transactional
    public BillingAccount subscribe(String storeId, BillingTier tier) {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("storeId is required");
        }
        if (tier == null) {
            throw new IllegalArgumentException("tier is required");
        }
        BillingAccount account =
                BillingAccount.builder()
                        .accountId(UUID.randomUUID().toString())
                        .storeId(storeId)
                        .tier(tier)
                        .status("ACTIVE")
                        .build();
        return port.saveAccount(account);
    }

    @Override
    @Transactional
    public BillingAccount changeTier(String accountId, BillingTier newTier) {
        if (newTier == null) {
            throw new IllegalArgumentException("tier is required");
        }
        BillingAccount account = requireAccount(accountId);
        account.setTier(newTier);
        return port.saveAccount(account);
    }

    @Override
    @Transactional
    public Invoice generateInvoice(String accountId, LocalDate periodStart, LocalDate periodEnd) {
        BillingAccount account = requireAccount(accountId);
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new IllegalStateException(
                    "Cannot invoice a non-active account (status=" + account.getStatus() + ")");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Invalid billing period");
        }
        // Flat-tier billing: the invoice amount is the tier's monthly fee, usage-independent.
        Invoice invoice =
                Invoice.builder()
                        .invoiceId(UUID.randomUUID().toString())
                        .accountId(accountId)
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .amount(account.getTier().getMonthlyFee())
                        .currency("CHF")
                        .status("ISSUED")
                        .issuedAt(OffsetDateTime.now())
                        .build();
        return port.saveInvoice(invoice);
    }

    @Override
    @Transactional
    public Invoice markInvoicePaid(String invoiceId) {
        Invoice invoice =
                port.findInvoiceById(invoiceId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Invoice not found: " + invoiceId));
        if ("PAID".equals(invoice.getStatus())) {
            return invoice; // idempotent
        }
        if (!"ISSUED".equals(invoice.getStatus())) {
            throw new IllegalStateException(
                    "Only ISSUED invoices can be paid (status=" + invoice.getStatus() + ")");
        }
        invoice.setStatus("PAID");
        invoice.setPaidAt(OffsetDateTime.now());
        return port.saveInvoice(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> getInvoices(String accountId) {
        return port.findInvoicesByAccountId(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillingAccount> getAccount(String accountId) {
        return port.findAccountById(accountId);
    }

    private BillingAccount requireAccount(String accountId) {
        return port.findAccountById(accountId)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Billing account not found: " + accountId));
    }
}
