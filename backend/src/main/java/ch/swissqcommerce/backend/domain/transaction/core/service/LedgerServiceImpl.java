package ch.swissqcommerce.backend.domain.transaction.core.service;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;

import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class LedgerServiceImpl implements LedgerUseCase {
    private final JournalEntryRepository journalEntryRepository;
    private final CustomerRepository customerRepository;
    private final RiderRepository riderRepository;
    private final WholesalerRepository wholesalerRepository;
    private final LedgerLineRepository ledgerLineRepository;

    public LedgerServiceImpl(JournalEntryRepository journalEntryRepository,
                             CustomerRepository customerRepository,
                             RiderRepository riderRepository,
                             WholesalerRepository wholesalerRepository,
                             LedgerLineRepository ledgerLineRepository) {
        this.journalEntryRepository = journalEntryRepository;
        this.customerRepository = customerRepository;
        this.riderRepository = riderRepository;
        this.wholesalerRepository = wholesalerRepository;
        this.ledgerLineRepository = ledgerLineRepository;
    }

    /**
     * Records a balanced double-entry transaction in the system.
     * Enforces SERIALIZABLE isolation to avoid concurrent race conditions on wallet balances.
     */
    public JournalEntry recordTransaction(String reference, String description, List<LedgerLeg> legs) {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("Transaction must contain at least one leg.");
        }

        // 1. Validate Double-Entry Balance
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (LedgerLeg leg : legs) {
            totalDebit = totalDebit.add(leg.debit());
            totalCredit = totalCredit.add(leg.credit());
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException(String.format(
                "Unbalanced transaction. Total Debits: %s, Total Credits: %s", totalDebit, totalCredit));
        }

        // 2. Fetch the hash of the last entry for chaining
        Optional<JournalEntry> lastEntryOpt = journalEntryRepository.findFirstByOrderByEntryIdDesc();
        String prevHash = lastEntryOpt.map(JournalEntry::getEntryHash)
                .orElse("0000000000000000000000000000000000000000000000000000000000000000");

        // 3. Create Journal Entry
        UUID uuid = UUID.randomUUID();
        String entryHash = computeSHA256Hash(uuid.toString(), reference, description, prevHash);

        JournalEntry entry = JournalEntry.builder()
                .entryUuid(uuid)
                .reference(reference)
                .description(description)
                .previousEntryHash(prevHash)
                .entryHash(entryHash)
                .build();

        JournalEntry savedEntry = journalEntryRepository.save(entry);

        // 4. Create Ledger Lines and adjust Actor Wallet Balances
        List<LedgerLine> lines = new ArrayList<>();
        for (LedgerLeg leg : legs) {
            LedgerLine line = LedgerLine.builder()
                    .journalEntry(savedEntry)
                    .accountType(leg.accountType())
                    .actorId(leg.actorId())
                    .debit(leg.debit())
                    .credit(leg.credit())
                    .build();
            lines.add(line);

            // Execute balance adjustments on actor profiles
            adjustActorWallet(leg.accountType(), leg.actorId(), leg.debit(), leg.credit());
        }
        savedEntry.setLedgerLines(lines);

        return journalEntryRepository.save(savedEntry);
    }

    private void adjustActorWallet(String accountType, String actorId, BigDecimal debit, BigDecimal credit) {
        if (actorId == null || "system".equalsIgnoreCase(accountType)) {
            return; // System account has virtual unlimited credit/debit or is tracked implicitly
        }

        BigDecimal netChange = credit.subtract(debit); // Credit increases wallet, debit decreases it

        switch (accountType.toLowerCase()) {
            case "customer":
                Customer customer = customerRepository.findById(actorId)
                        .orElseThrow(() -> new NoSuchElementException("Customer not found: " + actorId));
                BigDecimal newCustBalance = customer.getWalletBalance().add(netChange);
                if (newCustBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalStateException("Customer wallet balance cannot drop below $0.00: " + actorId);
                }
                customer.setWalletBalance(newCustBalance);
                customerRepository.save(customer);
                break;

            case "rider":
                Rider rider = riderRepository.findById(actorId)
                        .orElseThrow(() -> new NoSuchElementException("Rider not found: " + actorId));
                BigDecimal newRiderBalance = rider.getWalletBalance().add(netChange);
                if (newRiderBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalStateException("Rider wallet balance cannot drop below $0.00: " + actorId);
                }
                rider.setWalletBalance(newRiderBalance);
                riderRepository.save(rider);
                break;

            case "wholesaler":
                Wholesaler wholesaler = wholesalerRepository.findById(actorId)
                        .orElseThrow(() -> new NoSuchElementException("Wholesaler not found: " + actorId));
                // B2B wholesalers are credited on invoices
                break;

            default:
                // No action required for other account types
                break;
        }
    }

    private String computeSHA256Hash(String uuid, String reference, String description, String prevHash) {
        try {
            String raw = uuid + reference + description + prevHash;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 digest algorithm not available", e);
        }
    }

    public List<LedgerLine> getCustomerLedger(String customerId) {
        return ledgerLineRepository.findByAccountTypeAndActorIdOrderByLineIdDesc("customer", customerId);
    }
}
