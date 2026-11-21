package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CategoryServiceTest {
    @TempDir Path dir;
    Fixture fx;
    CategoryService categories;
    static final LocalDate D = LocalDate.of(2025, 9, 10);

    @BeforeEach
    void setUp() throws Exception {
        fx = Fixture.in(dir);
        categories = fx.categories;
    }

    private Transaction spend(UUID category, long amount) throws Exception {
        Transaction t = new Expense(D, category, amount);
        fx.finance.add(t);
        return t;
    }

    // ---- defaults ----

    @Test
    void firstRunSeedsEditableDefaults() {
        assertFalse(categories.active(CategoryType.EXPENSE).isEmpty());
        assertFalse(categories.active(CategoryType.INCOME).isEmpty());
        assertTrue(categories.getAll().stream().anyMatch(c -> c.getName().equals("Tabungan")));
    }

    @Test
    void secondRunKeepsEditsInsteadOfReseeding() throws Exception {
        Category makan = categories.getAll().stream().filter(c -> c.getName().equals("Makan")).findFirst().orElseThrow();
        categories.rename(makan.getId(), "Jajan");
        int count = categories.getAll().size();

        Fixture again = fx.reload();
        assertEquals(count, again.categories.getAll().size());
        assertEquals("Jajan", again.categories.nameOf(makan.getId()));
        assertTrue(again.categories.getAll().stream().noneMatch(c -> c.getName().equals("Makan")));
    }

    // ---- rename propagation ----

    @Test
    void renamePropagatesToEveryTransactionBecauseTheyOnlyHoldTheId() throws Exception {
        UUID makan = fx.expense("Makan");
        Transaction a = spend(makan, 10_000L);
        Transaction b = spend(makan, 20_000L);

        categories.rename(makan, "Makan & Minum");

        assertEquals(makan, a.getCategoryId());                       // transactions untouched
        assertEquals(makan, b.getCategoryId());
        assertEquals("Makan & Minum", categories.nameOf(a.getCategoryId()));
        // and the grouping used by the dashboard still sees one category, now with the new name
        var byCategory = fx.finance.expenseByCategory(YearMonth.of(2025, 9));
        assertEquals(1, byCategory.size());
        assertEquals(30_000L, byCategory.get(makan));
    }

    @Test
    void renameSurvivesReload() throws Exception {
        UUID makan = fx.expense("Makan");
        spend(makan, 10_000L);
        categories.rename(makan, "Jajan");

        Fixture again = fx.reload();
        assertEquals("Jajan", again.categories.nameOf(again.finance.getAll().get(0).getCategoryId()));
    }

    @Test
    void recolorAndBudgetKeepTheIdentity() throws Exception {
        UUID makan = fx.expense("Makan");
        categories.recolor(makan, "#00ff00");
        categories.setBudget(makan, 750_000L);
        assertEquals("#00ff00", categories.require(makan).getColor());
        assertEquals(750_000L, categories.require(makan).getMonthlyBudget());
        categories.setBudget(makan, null);
        assertNull(categories.require(makan).getMonthlyBudget());
        assertThrows(InvalidCategoryException.class, () -> categories.setBudget(makan, 0L));
        assertThrows(InvalidCategoryException.class, () -> categories.setBudget(fx.income("Gaji"), 1_000L));
    }

    // ---- duplicate names ----

    @Test
    void namesAreUniquePerTypeIgnoringCase() throws Exception {
        categories.add("Hobi", CategoryType.EXPENSE, "gift", "#3b82c4", null);
        assertThrows(InvalidCategoryException.class,
                () -> categories.add("  hobi ", CategoryType.EXPENSE, "gift", "#3b82c4", null));
        // the same name is fine on the other type
        assertDoesNotThrow(() -> categories.add("Hobi", CategoryType.INCOME, "gift", "#3b82c4", null));
    }

    @Test
    void renameIntoAnExistingNameIsRefusedButCaseOnlyChangeIsAllowed() throws Exception {
        UUID makan = fx.expense("Makan");
        UUID transport = fx.expense("Transport");
        assertThrows(InvalidCategoryException.class, () -> categories.rename(transport, "makan"));
        assertEquals("Transport", categories.nameOf(transport));   // unchanged
        categories.rename(makan, "MAKAN");                          // renaming to itself differently cased is fine
        assertEquals("MAKAN", categories.nameOf(makan));
    }

    // ---- delete ----

    @Test
    void deleteWithTransactionsNeedsATarget() throws Exception {
        UUID makan = fx.expense("Makan");
        Transaction t = spend(makan, 10_000L);

        assertEquals(1, categories.usageCount(makan));
        assertThrows(InvalidCategoryException.class, () -> categories.delete(makan, null));
        assertTrue(categories.find(makan).isPresent());     // refused: nothing deleted
        assertEquals(makan, t.getCategoryId());
        assertEquals(1, fx.finance.getAll().size());
    }

    @Test
    void deleteWithReassignMovesTransactionsFirst() throws Exception {
        UUID makan = fx.expense("Makan");
        UUID jajan = fx.expense("Jajan");
        Transaction a = spend(makan, 10_000L);
        Transaction b = spend(makan, 20_000L);
        Transaction other = spend(jajan, 5_000L);

        categories.delete(makan, jajan);

        assertTrue(categories.find(makan).isEmpty());
        assertEquals(jajan, a.getCategoryId());
        assertEquals(jajan, b.getCategoryId());
        assertEquals(jajan, other.getCategoryId());
        assertEquals(3, fx.finance.getAll().size());                // no transaction lost
        assertEquals(35_000L, fx.finance.expenseByCategory(YearMonth.of(2025, 9)).get(jajan));
        assertTrue(fx.finance.getAll().stream().allMatch(t -> categories.find(t.getCategoryId()).isPresent())); // no orphans
    }

    @Test
    void deleteWithoutTransactionsNeedsNoTarget() throws Exception {
        UUID unused = categories.add("Kosong", CategoryType.EXPENSE, "other", "#7d8b99", null).getId();
        categories.delete(unused, null);
        assertTrue(categories.find(unused).isEmpty());
    }

    @Test
    void reassignTargetMustBeValid() throws Exception {
        UUID makan = fx.expense("Makan");
        UUID gaji = fx.income("Gaji");
        UUID archived = categories.add("Lama", CategoryType.EXPENSE, "other", "#7d8b99", null).getId();
        categories.archive(archived, true);
        spend(makan, 10_000L);

        assertThrows(InvalidCategoryException.class, () -> categories.delete(makan, makan));            // itself
        assertThrows(InvalidCategoryException.class, () -> categories.delete(makan, gaji));             // other type
        assertThrows(InvalidCategoryException.class, () -> categories.delete(makan, archived));         // archived
        assertThrows(InvalidCategoryException.class, () -> categories.delete(makan, UUID.randomUUID())); // unknown
        assertTrue(categories.find(makan).isPresent());
        assertEquals(makan, fx.finance.getAll().get(0).getCategoryId());
    }

    // ---- merge ----

    @Test
    void mergeMovesTransactionsAndRemovesTheSource() throws Exception {
        UUID bensin = fx.expense("Bensin");
        UUID transport = fx.expense("Transport");
        Transaction a = spend(bensin, 50_000L);
        Transaction b = spend(transport, 20_000L);

        categories.merge(bensin, transport);

        assertTrue(categories.find(bensin).isEmpty());
        assertEquals(transport, a.getCategoryId());
        assertEquals(transport, b.getCategoryId());
        assertEquals(70_000L, fx.finance.expenseByCategory(YearMonth.of(2025, 9)).get(transport));
    }

    @Test
    void mergeNeedsADifferentTargetOfTheSameType() throws Exception {
        UUID bensin = fx.expense("Bensin");
        assertThrows(InvalidCategoryException.class, () -> categories.merge(bensin, null));
        assertThrows(InvalidCategoryException.class, () -> categories.merge(bensin, bensin));
        assertThrows(InvalidCategoryException.class, () -> categories.merge(bensin, fx.income("Gaji")));
        assertTrue(categories.find(bensin).isPresent());
    }

    // ---- archive ----

    @Test
    void archivedCategoriesLeavePickersButKeepTheirTransactions() throws Exception {
        UUID makan = fx.expense("Makan");
        Transaction t = spend(makan, 10_000L);

        categories.archive(makan, true);
        assertTrue(categories.active(CategoryType.EXPENSE).stream().noneMatch(c -> c.getId().equals(makan)));
        assertThrows(InvalidCategoryException.class, () -> categories.requireUsable(makan, CategoryType.EXPENSE));
        assertEquals(makan, t.getCategoryId());                       // nothing lost
        assertEquals(1, fx.finance.getAll().size());

        categories.archive(makan, false);
        assertDoesNotThrow(() -> categories.requireUsable(makan, CategoryType.EXPENSE));
    }

    @Test
    void requireUsableChecksTypeToo() throws Exception {
        assertThrows(InvalidCategoryException.class, () -> categories.requireUsable(fx.income("Gaji"), CategoryType.EXPENSE));
        assertThrows(InvalidCategoryException.class, () -> categories.requireUsable(UUID.randomUUID(), CategoryType.EXPENSE));
    }

    // ---- listeners / persistence ----

    @Test
    void changesNotifyListenersAndReachDisk() throws Exception {
        int[] calls = {0};
        categories.addListener(() -> calls[0]++);
        UUID id = categories.add("Hobi", CategoryType.EXPENSE, "gift", "#8a63d2", 100_000L).getId();
        assertEquals(1, calls[0]);

        Category back = fx.reload().categories.require(id);
        assertEquals("Hobi", back.getName());
        assertEquals("#8a63d2", back.getColor());
        assertEquals(100_000L, back.getMonthlyBudget());
    }
}
