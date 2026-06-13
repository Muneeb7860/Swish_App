package ch.swissqcommerce.backend.domain.billing.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.billing.core.model.Invoice;
import ch.swissqcommerce.backend.domain.billing.port.out.BillingPort;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BillingPersistenceAdapter implements BillingPort {

    private final BillingAccountRepository accountRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    public BillingAccount saveAccount(BillingAccount account) {
        BillingAccountEntity entity =
                BillingAccountEntity.builder()
                        .accountId(account.getAccountId())
                        .storeId(account.getStoreId())
                        .tier(account.getTier().name())
                        .status(account.getStatus())
                        .build();
        return toDomain(accountRepository.save(entity));
    }

    @Override
    public Optional<BillingAccount> findAccountById(String accountId) {
        return accountRepository.findById(accountId).map(this::toDomain);
    }

    @Override
    public Invoice saveInvoice(Invoice invoice) {
        InvoiceEntity entity =
                InvoiceEntity.builder()
                        .invoiceId(invoice.getInvoiceId())
                        .accountId(invoice.getAccountId())
                        .periodStart(invoice.getPeriodStart())
                        .periodEnd(invoice.getPeriodEnd())
                        .amount(invoice.getAmount())
                        .currency(invoice.getCurrency())
                        .status(invoice.getStatus())
                        .issuedAt(invoice.getIssuedAt())
                        .paidAt(invoice.getPaidAt())
                        .build();
        return toDomain(invoiceRepository.save(entity));
    }

    @Override
    public Optional<Invoice> findInvoiceById(String invoiceId) {
        return invoiceRepository.findById(invoiceId).map(this::toDomain);
    }

    @Override
    public List<Invoice> findInvoicesByAccountId(String accountId) {
        return invoiceRepository.findByAccountIdOrderByIssuedAtDesc(accountId).stream()
                .map(this::toDomain)
                .toList();
    }

    private BillingAccount toDomain(BillingAccountEntity e) {
        return BillingAccount.builder()
                .accountId(e.getAccountId())
                .storeId(e.getStoreId())
                .tier(BillingTier.valueOf(e.getTier()))
                .status(e.getStatus())
                .build();
    }

    private Invoice toDomain(InvoiceEntity e) {
        return Invoice.builder()
                .invoiceId(e.getInvoiceId())
                .accountId(e.getAccountId())
                .periodStart(e.getPeriodStart())
                .periodEnd(e.getPeriodEnd())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .status(e.getStatus())
                .issuedAt(e.getIssuedAt())
                .paidAt(e.getPaidAt())
                .build();
    }
}
