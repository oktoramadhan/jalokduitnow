package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.schema.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {
    private static final LocalDate D = LocalDate.of(2025, 8, 1);
    private static final java.util.UUID CAT = java.util.UUID.randomUUID();

    @Test
    void incomeIsPositiveExpenseIsNegative() throws Exception {
        Transaction in = new Income(D, CAT, 5_000_000L);
        Transaction out = new Expense(D, CAT, 25_000L);
        assertEquals(5_000_000L, in.signedAmount());
        assertEquals(-25_000L, out.signedAmount());
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThrows(InvalidTransactionException.class,
                () -> new Expense(D, CAT, 0));
    }

    @Test
    void referencesItsCategoryByIdAndRequiresOne() throws Exception {
        Transaction t = new Expense(D, CAT, 1_000L);
        assertEquals(CAT, t.getCategoryId());
        assertEquals(CategoryType.EXPENSE, t.categoryType());
        assertEquals("Expense", t.typeLabel());
        assertEquals("Income", new Income(D, CAT, 1L).typeLabel());
        assertThrows(InvalidTransactionException.class, () -> new Expense(D, null, 1_000L));
        assertThrows(InvalidTransactionException.class, () -> t.setCategoryId(null));
        assertEquals(CAT, t.getCategoryId());
    }

    @Test
    void customValuesAreValidatedAgainstTheColumn() throws Exception {
        ColumnDefinition qty = new ColumnDefinition("q", "Qty", new NumberType(), false);
        ColumnDefinition kind = new ColumnDefinition("k", "Jenis", new ChoiceType(List.of("Need", "Want")), false);
        Transaction t = new Expense(D, CAT, 10_000L);

        t.setCustomValue(qty, 3L);
        t.setCustomValue(kind, "Need");
        assertEquals(3L, t.getCustomValue("q"));

        assertThrows(InvalidColumnException.class, () -> t.setCustomValue(qty, "tiga"));  // wrong class
        assertThrows(InvalidColumnException.class, () -> t.setCustomValue(kind, "Other")); // not an option
        assertEquals(3L, t.getCustomValue("q"));                                           // unchanged after errors

        assertThrows(UnsupportedOperationException.class, () -> t.getCustomValues().put("x", 1));
        t.setCustomValue(qty, null);
        assertNull(t.getCustomValue("q"));
    }

    @Test
    void replaceCustomValuesIsAllOrNothing() throws Exception {
        TableSchema schema = new TableSchema();
        schema.add(new ColumnDefinition("q", "Qty", new NumberType(), false));
        Transaction t = new Expense(D, CAT, 10_000L);
        t.replaceCustomValues(schema, Map.of("q", 1L));

        assertThrows(InvalidColumnException.class, () -> t.replaceCustomValues(schema, Map.of("q", 2L, "ghost", "x")));
        assertEquals(1L, t.getCustomValue("q"));
    }
}
