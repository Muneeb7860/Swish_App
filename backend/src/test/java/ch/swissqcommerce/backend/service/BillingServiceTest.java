package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.billing.core.model.Invoice;
import ch.swissqcommerce.backend.domain.billing.core.service.BillingServiceImpl;
import ch.swissqcommerce.backend.domain.billing.port.out.BillingPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private BillingPort port;

    @InjectMocks private BillingServiceImpl service;

    private BillingAccount activeAccount(String id, BillingTier tier) {
        return BillingAccount.builder()
                .accountId(id)
                .storeId("store-1")
                .tier(tier)
                .status("ACTIVE")
                .build();
    }

    @Test
    void subscribe_createsActiveAccount() {
        when(port.saveAccount(any())).thenAnswer(i -> i.getArgument(0));

        BillingAccount a = service.subscribe("store-1", BillingTier.PRO);

        assertNotNull(a.getAccountId());
        assertEquals("store-1", a.getStoreId());
        assertEquals(BillingTier.PRO, a.getTier());
        assertEquals("ACTIVE", a.getStatus());
    }

    @Test
    void subscribe_blankStore_rejected() {
        assertThrows(
                IllegalArgumentException.class, () -> service.subscribe("  ", BillingTier.BASIC));
        verify(port, never()).saveAccount(any());
    }

    @Test
    void generateInvoice_usesFlatTierAmount() {
        when(port.findAccountById("acc-1"))
                .thenReturn(Optional.of(activeAccount("acc-1", BillingTier.PRO)));
        when(port.saveInvoice(any())).thenAnswer(i -> i.getArgument(0));

        Invoice inv =
                service.generateInvoice(
                        "acc-1", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertEquals(0, new BigDecimal("249.00").compareTo(inv.getAmount()));
        assertEquals("ISSUED", inv.getStatus());
        assertEquals("CHF", inv.getCurrency());
        assertNotNull(inv.getIssuedAt());
        assertNull(inv.getPaidAt());
    }

    @Test
    void generateInvoice_nonActiveAccount_rejected() {
        BillingAccount suspended = activeAccount("acc-2", BillingTier.BASIC);
        suspended.setStatus("SUSPENDED");
        when(port.findAccountById("acc-2")).thenReturn(Optional.of(suspended));

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.generateInvoice(
                                "acc-2", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
        verify(port, never()).saveInvoice(any());
    }

    @Test
    void generateInvoice_invalidPeriod_rejected() {
        when(port.findAccountById("acc-3"))
                .thenReturn(Optional.of(activeAccount("acc-3", BillingTier.BASIC)));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.generateInvoice(
                                "acc-3", LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)));
    }

    @Test
    void generateInvoice_missingAccount_notFound() {
        when(port.findAccountById("nope")).thenReturn(Optional.empty());
        assertThrows(
                java.util.NoSuchElementException.class,
                () ->
                        service.generateInvoice(
                                "nope", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
    }

    @Test
    void markInvoicePaid_issuedBecomesPaid() {
        Invoice issued =
                Invoice.builder().invoiceId("inv-1").accountId("acc-1").status("ISSUED").build();
        when(port.findInvoiceById("inv-1")).thenReturn(Optional.of(issued));
        when(port.saveInvoice(any())).thenAnswer(i -> i.getArgument(0));

        Invoice paid = service.markInvoicePaid("inv-1");

        assertEquals("PAID", paid.getStatus());
        assertNotNull(paid.getPaidAt());
    }

    @Test
    void markInvoicePaid_idempotentWhenAlreadyPaid() {
        Invoice alreadyPaid = Invoice.builder().invoiceId("inv-2").status("PAID").build();
        when(port.findInvoiceById("inv-2")).thenReturn(Optional.of(alreadyPaid));

        Invoice result = service.markInvoicePaid("inv-2");

        assertEquals("PAID", result.getStatus());
        verify(port, never()).saveInvoice(any());
    }

    @Test
    void markInvoicePaid_draftRejected() {
        Invoice draft = Invoice.builder().invoiceId("inv-3").status("DRAFT").build();
        when(port.findInvoiceById("inv-3")).thenReturn(Optional.of(draft));

        assertThrows(IllegalStateException.class, () -> service.markInvoicePaid("inv-3"));
        verify(port, never()).saveInvoice(any());
    }
}
