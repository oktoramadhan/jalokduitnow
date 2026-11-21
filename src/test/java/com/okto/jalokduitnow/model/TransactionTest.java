package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidTransactionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {
    private static final LocalDate D = LocalDate.of(2025, 8, 1);

    @Test
    void incomeIsPositiveExpenseIsNegative() throws Exception {
        Transaction in = new Income(D, new Category("Gaji"), 5_000_000L, "gaji");
        Transaction out = new Expense(D, new Category("Makan"), 25_000L, "");
        assertEquals(5_000_000L, in.signedAmount());
        assertEquals(-25_000L, out.signedAmount());
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThrows(InvalidTransactionException.class,
                () -> new Expense(D, new Category("Makan"), 0, ""));
    }

    @Test
    void rejectsBlankCategory() {
        assertThrows(IllegalArgumentException.class, () -> new Category("  "));
    }
}
