package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.model.*;
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
    private WholesalerRepository wholesalerRepository;
    @Mock
    private LedgerLineRepository ledgerLineRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Test
    public void testRecordTransaction_Success() {
        Customer cust = new Customer();
        cust.setCustomerId("C1");
        cust.setWalletBalance(new BigDecimal("100.00"));

        when(customerRepository.findById("C1")).thenReturn(Optional.of(cust));
        when(journalEntryRepository.findFirstByOrderByEntryIdDesc()).thenReturn(Optional.empty());
        
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(i -> i.getArgument(0));

        List<LedgerService.LedgerLeg> legs = List.of(
            new LedgerService.LedgerLeg("customer", "C1", new BigDecimal("10.00"), BigDecimal.ZERO),
            new LedgerService.LedgerLeg("system", null, BigDecimal.ZERO, new BigDecimal("10.00"))
        );

        JournalEntry result = ledgerService.recordTransaction("REF", "DESC", legs);

        assertNotNull(result);
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", result.getPreviousEntryHash());
        assertNotNull(result.getEntryHash());
        verify(customerRepository, times(1)).save(any(Customer.class));
        assertEquals(new BigDecimal("90.00"), cust.getWalletBalance());
    }
}
