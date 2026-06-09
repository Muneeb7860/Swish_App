package com.platform.shared.ledger;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class FinancialLedgerEntryTest {

    @Test
    public void testDefaultConstructor() {
        FinancialLedgerEntry entry = new FinancialLedgerEntry();
        assertNull(entry.getEntryId());
        assertNull(entry.getAccountId());
        assertNull(entry.getOrderId());
        assertNull(entry.getEntryType());
        assertEquals(BigDecimal.ZERO, entry.getDebitAmount());
        assertEquals(BigDecimal.ZERO, entry.getCreditAmount());
        assertEquals("INR", entry.getCurrency());
        assertNotNull(entry.getCreatedAt());
    }

    @Test
    public void testParameterizedConstructor() {
        UUID accountId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal debit = new BigDecimal("150.00");
        BigDecimal credit = BigDecimal.ZERO;
        
        FinancialLedgerEntry entry = new FinancialLedgerEntry(
                accountId, orderId, "DEBIT", debit, credit, "CHF"
        );

        assertEquals(accountId, entry.getAccountId());
        assertEquals(orderId, entry.getOrderId());
        assertEquals("DEBIT", entry.getEntryType());
        assertEquals(debit, entry.getDebitAmount());
        assertEquals(credit, entry.getCreditAmount());
        assertEquals("CHF", entry.getCurrency());
        assertNotNull(entry.getCreatedAt());
    }

    @Test
    public void testNullCurrencyDefaultsToINR() {
        UUID accountId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        
        FinancialLedgerEntry entry = new FinancialLedgerEntry(
                accountId, orderId, "CREDIT", BigDecimal.ZERO, new BigDecimal("200.00"), null
        );

        assertEquals("INR", entry.getCurrency());
    }
}
