package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Files from before categories had ids saved only a category name; they must load without losing anything. */
class CategoryMigrationTest {
    private static String legacyRow(String type, String category, long amount) {
        return """
                {"id":"%s","type":"%s","date":"2025-08-03","category":"%s","amount":%d,"description":"catatan"}"""
                .formatted(UUID.randomUUID(), type, category, amount);
    }

    @Test
    void oldNamesBecomeIdsAndTheFileIsRewritten(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("transactions.json"), "[" + String.join(",",
                legacyRow("Expense", "Makan", 10_000),
                legacyRow("Expense", "makan ", 20_000),      // same category, different spelling
                legacyRow("Expense", "Kopi Spesial", 5_000), // not a default: must be created
                legacyRow("Income", "Gaji", 1_000_000)) + "]");

        Fixture fx = Fixture.in(dir);
        List<Transaction> all = fx.finance.getAll();
        assertEquals(4, all.size());

        UUID makan = fx.expense("Makan");
        assertEquals(2, all.stream().filter(t -> t.getCategoryId().equals(makan)).count()); // matched the default, one id
        UUID kopi = all.stream().filter(t -> t.getAmount() == 5_000).findFirst().orElseThrow().getCategoryId();
        assertEquals("Kopi Spesial", fx.categories.nameOf(kopi));
        assertEquals(CategoryType.EXPENSE, fx.categories.require(kopi).getType());
        // "Gaji" income is not confused with an expense category of the same name
        assertEquals(CategoryType.INCOME, fx.categories.require(
                all.stream().filter(t -> t.signedAmount() > 0).findFirst().orElseThrow().getCategoryId()).getType());

        // the migrated data was saved: the file now holds ids and no legacy name
        Fixture again = fx.reload();
        String json = Files.readString(dir.resolve("transactions.json"));
        assertTrue(json.contains("categoryId"));
        assertFalse(json.contains("\"category\""));
        assertEquals(4, again.finance.getAll().size());
        assertEquals("Kopi Spesial", again.categories.nameOf(kopi));
        assertEquals("catatan", again.finance.getAll().get(0).getCustomValue("keterangan")); // description migration still works
    }

    @Test
    void unknownCategoryIdsFallBackInsteadOfLeavingOrphans(@TempDir Path dir) throws Exception {
        UUID ghost = UUID.randomUUID();
        Files.writeString(dir.resolve("transactions.json"), """
                [{"id":"%s","type":"Expense","date":"2025-08-03","categoryId":"%s","amount":9000,"custom":{}}]
                """.formatted(UUID.randomUUID(), ghost));

        Fixture fx = Fixture.in(dir);
        Transaction t = fx.finance.getAll().get(0);
        assertNotEquals(ghost, t.getCategoryId());
        assertTrue(fx.categories.find(t.getCategoryId()).isPresent());     // no orphan
        assertEquals("Tanpa Kategori", fx.categories.nameOf(t.getCategoryId()));
        assertEquals(9_000L, t.getAmount());                                // data kept
    }

    @Test
    void modernFileRoundTripsWithoutChanges(@TempDir Path dir) throws Exception {
        Fixture fx = Fixture.in(dir);
        fx.finance.add(new com.okto.jalokduitnow.model.Expense(java.time.LocalDate.of(2025, 9, 1), fx.expense("Makan"), 1_000L));
        Fixture again = fx.reload();
        assertEquals(fx.finance.getAll().get(0).getCategoryId(), again.finance.getAll().get(0).getCategoryId());
        assertEquals(1, again.finance.getAll().size());
    }
}
