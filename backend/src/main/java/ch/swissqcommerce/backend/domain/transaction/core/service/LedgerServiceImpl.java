package ch.swissqcommerce.backend.domain.transaction.core.service;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import ch.swissqcommerce.backend.model.Customer;


import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;

import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.JournalEntryEntity;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.LedgerLineEntity;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class LedgerServiceImpl implements LedgerUseCase {
    private final JournalEntryRepository journalEntryRepository;
    private final CustomerRepository customerRepository;
    private final EnrollmentOutPort enrollmentOutPort;
    private final WholesalerPort wholesalerPort;
    private final LedgerLineRepository ledgerLineRepository;

    public LedgerServiceImpl(JournalEntryRepository journalEntryRepository,
                             CustomerRepository customerRepository,
                             EnrollmentOutPort enrollmentOutPort,
                             WholesalerPort wholesalerPort,
                             LedgerLineRepository ledgerLineRepository) {
        this.journalEntryRepository = journalEntryRepository;
        this.customerRepository = customerRepository;
        this.enrollmentOutPort = enrollmentOutPort;
        this.wholesalerPort = wholesalerPort;
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
        Optional<JournalEntryEntity> lastEntryOpt = journalEntryRepository.findFirstByOrderByEntryIdDesc();
        String prevHash = lastEntryOpt.map(JournalEntryEntity::getEntryHash)
                .orElse("0000000000000000000000000000000000000000000000000000000000000000");

        // 3. Create Journal Entry
        UUID uuid = UUID.randomUUID();
        String entryHash = computeSHA256Hash(uuid.toString(), reference, description, prevHash);

        JournalEntryEntity entryEntity = JournalEntryEntity.builder()
                .entryUuid(uuid)
                .reference(reference)
                .description(description)
                .previousEntryHash(prevHash)
                .entryHash(entryHash)
                .build();

        JournalEntryEntity savedEntry = journalEntryRepository.save(entryEntity);

        // 4. Create Ledger Lines and adjust Actor Wallet Balances
        List<LedgerLineEntity> lineEntities = new ArrayList<>();
        List<LedgerLine> lines = new ArrayList<>();
        for (LedgerLeg leg : legs) {
            LedgerLineEntity lineEntity = LedgerLineEntity.builder()
                    .journalEntry(savedEntry)
                    .accountType(leg.accountType())
                    .actorId(leg.actorId())
                    .debit(leg.debit())
                    .credit(leg.credit())
                    .build();
            lineEntities.add(lineEntity);

            lines.add(LedgerLine.builder()
                    .lineId(lineEntity.getLineId())
                    .accountType(leg.accountType())
                    .actorId(leg.actorId())
                    .debit(leg.debit())
                    .credit(leg.credit())
                    .build());

            // Execute balance adjustments on actor profiles
            adjustActorWallet(leg.accountType(), leg.actorId(), leg.debit(), leg.credit());
        }
        savedEntry.setLedgerLines(lineEntities);
        savedEntry = journalEntryRepository.save(savedEntry);

        return JournalEntry.builder()
                .entryId(savedEntry.getEntryId())
                .entryUuid(savedEntry.getEntryUuid())
                .timestamp(savedEntry.getTimestamp())
                .reference(savedEntry.getReference())
                .description(savedEntry.getDescription())
                .previousEntryHash(savedEntry.getPreviousEntryHash())
                .entryHash(savedEntry.getEntryHash())
                .ledgerLines(lines)
                .build();
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
                Rider rider = enrollmentOutPort.findRiderById(actorId)
                        .orElseThrow(() -> new NoSuchElementException("Rider not found: " + actorId));
                BigDecimal newRiderBalance = rider.getWalletBalance().add(netChange);
                if (newRiderBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalStateException("Rider wallet balance cannot drop below $0.00: " + actorId);
                }
                rider.setWalletBalance(newRiderBalance);
                enrollmentOutPort.saveRider(rider);
                break;

            case "wholesaler":
                Wholesaler wholesaler = wholesalerPort.findById(actorId)
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
        return ledgerLineRepository.findByAccountTypeAndActorIdOrderByLineIdDesc("customer", customerId)
                .stream()
                .map(entity -> LedgerLine.builder()
                        .lineId(entity.getLineId())
                        .accountType(entity.getAccountType())
                        .actorId(entity.getActorId())
                        .debit(entity.getDebit())
                        .credit(entity.getCredit())
                        .build())
                .toList();
    }
}
