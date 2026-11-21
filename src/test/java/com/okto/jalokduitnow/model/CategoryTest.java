package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidCategoryException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CategoryTest {
    @Test
    void hasAStableIdAndTrimmedName() throws Exception {
        Category c = Category.create("  Makan ", CategoryType.EXPENSE, "food", "#F0A030", 500_000L);
        UUID id = c.getId();
        c.update("Makan Siang", "coffee", "#112233", null);
        assertEquals(id, c.getId());                 // renaming never changes the id
        assertEquals("Makan Siang", c.getName());
        assertEquals("#112233", c.getColor());
        assertNull(c.getMonthlyBudget());
        assertFalse(c.isArchived());
    }

    @Test
    void blankIconGetsTheDefaultAndKeysAreChecked() throws Exception {
        assertEquals(Category.DEFAULT_ICON, Category.create("X", CategoryType.EXPENSE, " ", "#000000", null).getIconKey());
        assertEquals("food", Category.create("Y", CategoryType.EXPENSE, "food", "#000000", null).getIconKey());
        assertThrows(InvalidCategoryException.class, () -> Category.create("Z", CategoryType.EXPENSE, "Not A Key!", "#000000", null));
    }

    @Test
    void invalidValuesAreRejectedAndNothingChanges() throws Exception {
        Category c = Category.create("Makan", CategoryType.EXPENSE, "food", "#f0a030", 1_000L);
        assertThrows(InvalidCategoryException.class, () -> c.update("  ", "food", "#f0a030", 1_000L));
        assertThrows(InvalidCategoryException.class, () -> c.update("Ok", "food", "merah", 1_000L));
        assertThrows(InvalidCategoryException.class, () -> c.update("Ok", "food", "#f0a030", 0L));
        assertThrows(InvalidCategoryException.class, () -> c.update("Ok", "food", "#f0a030", -5L));
        assertEquals("Makan", c.getName());          // all-or-nothing
        assertEquals(1_000L, c.getMonthlyBudget());
    }

    @Test
    void incomeCategoriesCannotHaveABudget() {
        assertThrows(InvalidCategoryException.class,
                () -> Category.create("Gaji", CategoryType.INCOME, "work", "#1f9d6b", 1_000L));
    }

    @Test
    void typeLabelsRoundTrip() {
        assertEquals(CategoryType.INCOME, CategoryType.fromLabel("income"));
        assertEquals("Expense", CategoryType.EXPENSE.label());
        assertThrows(IllegalArgumentException.class, () -> CategoryType.fromLabel("Other"));
    }
}
