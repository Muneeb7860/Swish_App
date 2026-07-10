package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.transaction.core.model.JournalEntry;
import ch.swissqcommerce.backend.domain.transaction.core.service.LedgerServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LedgerServiceTest {

    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RiderRepository riderRepository;
    @Mock private WholesalerPort wholesalerPort;
    @Mock private LedgerLineRepository ledgerLineRepository;

    @InjectMocks private LedgerServiceImpl ledgerService;

    @Test
    public void testRecordTransaction_Success() {
        Customer cust = new Customer();
        cust.setCustomerId("C1");
        cust.setWalletBalance(new BigDecimal("100.00"));

        when(customerRepository.findById("C1")).thenReturn(Optional.of(cust));
        when(journalEntryRepository.findFirstByOrderByEntryIdDesc()).thenReturn(Optional.empty());

        when(journalEntryRepository.save(
                        any(
                                ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence
                                        .JournalEntryEntity.class)))
                .thenAnswer(i -> i.getArgument(0));

        List<LedgerUseCase.LedgerLeg> legs =
                List.of(
                        new LedgerUseCase.LedgerLeg(
                                "customer", "C1", new BigDecimal("10.00"), BigDecimal.ZERO),
                        new LedgerUseCase.LedgerLeg(
                                "system", null, BigDecimal.ZERO, new BigDecimal("10.00")));

        JournalEntry result = ledgerService.recordTransaction("REF", "DESC", legs);

        assertNotNull(result);
        assertEquals(
                "0000000000000000000000000000000000000000000000000000000000000000",
                result.getPreviousEntryHash());
        assertNotNull(result.getEntryHash());
        verify(customerRepository, times(1)).save(any(Customer.class));
        assertEquals(new BigDecimal("90.00"), cust.getWalletBalance());
    }

    @Test
    public void testRecordTransaction_Unbalanced() {
        List<LedgerUseCase.LedgerLeg> legs =
                List.of(
                        new LedgerUseCase.LedgerLeg(
                                "customer", "C1", new BigDecimal("10.00"), BigDecimal.ZERO),
                        new LedgerUseCase.LedgerLeg(
                                "system", null, BigDecimal.ZERO, new BigDecimal("5.00")));

        assertThrows(
                IllegalArgumentException.class,
                () -> ledgerService.recordTransaction("REF", "DESC", legs));
    }

    @Test
    public void testRecordTransaction_EmptyLegs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ledgerService.recordTransaction("REF", "DESC", List.of()));
    }

    @Test
    public void testRecordTransaction_NegativeWalletBalance() {
        Customer cust = new Customer();
        cust.setCustomerId("C1");
        cust.setWalletBalance(new BigDecimal("5.00"));

        when(customerRepository.findById("C1")).thenReturn(Optional.of(cust));
        when(journalEntryRepository.findFirstByOrderByEntryIdDesc()).thenReturn(Optional.empty());
        when(journalEntryRepository.save(
                        any(
                                ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence
                                        .JournalEntryEntity.class)))
                .thenAnswer(i -> i.getArgument(0));

        List<LedgerUseCase.LedgerLeg> legs =
                List.of(
                        new LedgerUseCase.LedgerLeg(
                                "customer", "C1", new BigDecimal("10.00"), BigDecimal.ZERO),
                        new LedgerUseCase.LedgerLeg(
                                "system", null, BigDecimal.ZERO, new BigDecimal("10.00")));

        assertThrows(
                IllegalStateException.class,
                () -> ledgerService.recordTransaction("REF", "DESC", legs));
    }

    @Test
    public void testGetCustomerLedger() {
        ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.LedgerLineEntity
                entity =
                        new ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence
                                .LedgerLineEntity();
        entity.setLineId(1);
        entity.setAccountType("customer");
        entity.setActorId("C1");
        entity.setDebit(BigDecimal.TEN);
        entity.setCredit(BigDecimal.ZERO);

        when(ledgerLineRepository.findByAccountTypeAndActorIdOrderByLineIdDesc("customer", "C1"))
                .thenReturn(List.of(entity));

        List<ch.swissqcommerce.backend.domain.transaction.core.model.LedgerLine> lines =
                ledgerService.getCustomerLedger("C1");

        assertNotNull(lines);
        assertEquals(1, lines.size());
        assertEquals("C1", lines.get(0).getActorId());
        assertEquals(BigDecimal.TEN, lines.get(0).getDebit());
    }

    // ── OWASP A08 (Software/Data Integrity): ledger hash-chain tamper detection ──

    private static final String GENESIS =
            "0000000000000000000000000000000000000000000000000000000000000000";

    /** Mirror of LedgerServiceImpl.computeSHA256Hash — builds valid chain fixtures. */
    private static String sha256(String uuid, String ref, String desc, String prev) {
        try {
            byte[] h =
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(
                                    (uuid + ref + desc + prev)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence
                    .JournalEntryEntity
            entry(int id, java.util.UUID uuid, String ref, String desc, String prev) {
        return ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence
                .JournalEntryEntity.builder()
                .entryId(id)
                .entryUuid(uuid)
                .reference(ref)
                .description(desc)
                .previousEntryHash(prev)
                .entryHash(sha256(uuid.toString(), ref, desc, prev))
                .build();
    }

    @Test
    public void testVerifyChainIntegrity_CleanChain_IsValid() {
        var u1 = java.util.UUID.randomUUID();
        var u2 = java.util.UUID.randomUUID();
        var u3 = java.util.UUID.randomUUID();
        var e1 = entry(1, u1, "R1", "D1", GENESIS);
        var e2 = entry(2, u2, "R2", "D2", e1.getEntryHash());
        var e3 = entry(3, u3, "R3", "D3", e2.getEntryHash());
        when(journalEntryRepository.findAllByOrderByEntryIdAsc()).thenReturn(List.of(e1, e2, e3));

        LedgerUseCase.LedgerIntegrityReport report = ledgerService.verifyChainIntegrity();

        assertTrue(report.valid(), "clean chain must verify");
        assertNull(report.firstBrokenEntryId());
    }

    @Test
    public void testVerifyChainIntegrity_TamperedContent_IsDetected() {
        var u1 = java.util.UUID.randomUUID();
        var u2 = java.util.UUID.randomUUID();
        var e1 = entry(1, u1, "R1", "D1", GENESIS);
        var e2 = entry(2, u2, "R2", "D2", e1.getEntryHash());
        // Tamper: mutate the description but leave the (now stale) stored hash.
        e2.setDescription("TAMPERED-AMOUNT");
        when(journalEntryRepository.findAllByOrderByEntryIdAsc()).thenReturn(List.of(e1, e2));

        LedgerUseCase.LedgerIntegrityReport report = ledgerService.verifyChainIntegrity();

        assertFalse(report.valid(), "tampered entry must be detected");
        assertEquals(2, report.firstBrokenEntryId());
    }

    @Test
    public void testVerifyChainIntegrity_DeletedEntry_BreaksLinkage() {
        var u1 = java.util.UUID.randomUUID();
        var u2 = java.util.UUID.randomUUID();
        var u3 = java.util.UUID.randomUUID();
        var e1 = entry(1, u1, "R1", "D1", GENESIS);
        var e2 = entry(2, u2, "R2", "D2", e1.getEntryHash());
        var e3 = entry(3, u3, "R3", "D3", e2.getEntryHash());
        // Delete e2 from the chain — e3 now links to a missing predecessor.
        when(journalEntryRepository.findAllByOrderByEntryIdAsc()).thenReturn(List.of(e1, e3));

        LedgerUseCase.LedgerIntegrityReport report = ledgerService.verifyChainIntegrity();

        assertFalse(report.valid(), "deletion must break chain linkage");
        assertEquals(3, report.firstBrokenEntryId());
    }
}
