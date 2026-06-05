package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.transaction.core.model.JournalEntry;
import ch.swissqcommerce.backend.domain.transaction.core.service.LedgerServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LedgerServiceTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private RiderRepository riderRepository;
    @Mock
    private WholesalerPort wholesalerPort;
    @Mock
    private LedgerLineRepository ledgerLineRepository;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    @Test
    public void testRecordTransaction_Success() {
        Customer cust = new Customer();
        cust.setCustomerId("C1");
        cust.setWalletBalance(new BigDecimal("100.00"));

        when(customerRepository.findById("C1")).thenReturn(Optional.of(cust));
        when(journalEntryRepository.findFirstByOrderByEntryIdDesc()).thenReturn(Optional.empty());
        
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(i -> i.getArgument(0));

        List<LedgerUseCase.LedgerLeg> legs = List.of(
            new LedgerUseCase.LedgerLeg("customer", "C1", new BigDecimal("10.00"), BigDecimal.ZERO),
            new LedgerUseCase.LedgerLeg("system", null, BigDecimal.ZERO, new BigDecimal("10.00"))
        );

        JournalEntry result = ledgerService.recordTransaction("REF", "DESC", legs);

        assertNotNull(result);
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", result.getPreviousEntryHash());
        assertNotNull(result.getEntryHash());
        verify(customerRepository, times(1)).save(any(Customer.class));
        assertEquals(new BigDecimal("90.00"), cust.getWalletBalance());
    }
}
