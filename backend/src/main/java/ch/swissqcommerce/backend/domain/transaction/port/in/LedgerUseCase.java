package ch.swissqcommerce.backend.domain.transaction.port.in;

import ch.swissqcommerce.backend.domain.transaction.core.model.JournalEntry;
import ch.swissqcommerce.backend.domain.transaction.core.model.LedgerLine;
import java.math.BigDecimal;
import java.util.List;

public interface LedgerUseCase {

    record LedgerLeg(String accountType, String actorId, BigDecimal debit, BigDecimal credit) {}

    /**
     * Result of a ledger hash-chain integrity audit (OWASP A08 — data integrity).
     *
     * @param valid true if every entry's recomputed hash matches its stored hash AND the
     *     previousEntryHash links to the prior entry's hash (genesis = 64 zeros)
     * @param firstBrokenEntryId entryId of the first tampered/broken entry, or null if valid
     * @param reason human-readable outcome
     */
    record LedgerIntegrityReport(boolean valid, Integer firstBrokenEntryId, String reason) {}

    JournalEntry recordTransaction(String reference, String description, List<LedgerLeg> legs);

    List<LedgerLine> getCustomerLedger(String customerId);

    /**
     * Walks the append-only journal and verifies its SHA-256 hash chain is intact — detects
     * tampered content, altered hashes, reordering, and deletion.
     */
    LedgerIntegrityReport verifyChainIntegrity();
}
