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
}
