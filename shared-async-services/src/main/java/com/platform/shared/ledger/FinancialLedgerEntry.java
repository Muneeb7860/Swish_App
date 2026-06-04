package com.platform.shared.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_ledger", indexes = {
    @Index(name = "idx_ledger_account", columnList = "account_id")
}, uniqueConstraints = {},
   // Enforce that a row cannot have both debit and credit simultaneously
   schema = "", catalog = "")
@org.hibernate.annotations.Check(constraints = "(debit_amount = 0 AND credit_amount > 0) OR (credit_amount = 0 AND debit_amount > 0) OR (debit_amount = 0 AND credit_amount = 0)")
public class FinancialLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "entry_id", updatable = false, nullable = false)
    private UUID entryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "entry_type", nullable = false, length = 50)
    private String entryType;

    @Column(name = "debit_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "INR";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    // Constructors
    public FinancialLedgerEntry() {}

    public FinancialLedgerEntry(UUID accountId, UUID orderId, String entryType, BigDecimal debitAmount, BigDecimal creditAmount, String currency) {
        this.accountId = accountId;
        this.orderId = orderId;
        this.entryType = entryType;
        this.debitAmount = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        this.creditAmount = creditAmount != null ? creditAmount : BigDecimal.ZERO;
        if (currency != null) {
            this.currency = currency;
        }
    }

    // Getters (Immutable pattern, no setters for financial data)
    public UUID getEntryId() { return entryId; }
    public UUID getAccountId() { return accountId; }
    public UUID getOrderId() { return orderId; }
    public String getEntryType() { return entryType; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
