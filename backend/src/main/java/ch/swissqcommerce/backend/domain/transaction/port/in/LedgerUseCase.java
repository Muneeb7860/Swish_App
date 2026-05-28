package ch.swissqcommerce.backend.domain.transaction.port.in;

import ch.swissqcommerce.backend.domain.transaction.core.model.JournalEntry;
import ch.swissqcommerce.backend.domain.transaction.core.model.LedgerLine;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerUseCase {
    
    record LedgerLeg(
        String accountType,
        String actorId,
        BigDecimal debit,
        BigDecimal credit
    ) {}

    JournalEntry recordTransaction(String reference, String description, List<LedgerLeg> legs);

    List<LedgerLine> getCustomerLedger(String customerId);
}
