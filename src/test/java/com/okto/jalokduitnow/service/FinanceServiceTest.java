package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.TransactionDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FinanceServiceTest {
    @Test
    void totalsAndPersistence(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("tx.json");
        var service = new FinanceService(new JsonRepository<>(file, TransactionDto.class));
        LocalDate d = LocalDate.of(2025, 9, 1);
        service.add(new Income(d, new Category("Gaji"), 1_000_000L, ""));
        Expense e = new Expense(d, new Category("Makan"), 300_000L, "");
        service.add(e);

        assertEquals(1_000_000L, service.totalIncome());
        assertEquals(300_000L, service.totalExpense());
        assertEquals(700_000L, service.balance());

        assertTrue(service.delete(e.getId()));
        var reloaded = new FinanceService(new JsonRepository<>(file, TransactionDto.class));
        reloaded.load();
        assertEquals(1, reloaded.getAll().size());
        assertEquals(1_000_000L, reloaded.balance());
    }

    @Test
    void monthlyAggregationsAndUpdate(@TempDir Path dir) throws Exception {
        var service = new FinanceService(new JsonRepository<>(dir.resolve("tx.json"), TransactionDto.class));
        service.add(new Income(LocalDate.of(2025, 9, 1), new Category("Gaji"), 1_000_000L, ""));
        service.add(new Expense(LocalDate.of(2025, 9, 5), new Category("Makan"), 100_000L, ""));
        service.add(new Expense(LocalDate.of(2025, 9, 6), new Category("Makan"), 50_000L, ""));
        Expense transport = new Expense(LocalDate.of(2025, 9, 7), new Category("Transport"), 200_000L, "");
        service.add(transport);
        service.add(new Expense(LocalDate.of(2025, 10, 1), new Category("Makan"), 10_000L, ""));

        var sept = java.time.YearMonth.of(2025, 9);
        assertEquals(350_000L, service.summaryFor(sept).expense());
        assertEquals(650_000L, service.summaryFor(sept).balance());
        assertEquals(java.util.List.of("Transport", "Makan"),
                new java.util.ArrayList<>(service.expenseByCategory(sept).keySet())); // biggest first
        assertEquals(2, service.summaryByMonth().size());

        // edit: same id, different amount and type
        service.update(new Income(transport.getId(), transport.getDate(), new Category("Bonus"), 5L, ""));
        assertEquals(150_000L, service.summaryFor(sept).expense());
        assertEquals(java.util.List.of("Bonus", "Gaji"), service.categoryNames("Income"));
    }

    @Test
    void listenersFireOnChange(@TempDir Path dir) throws Exception {
        var service = new FinanceService(new JsonRepository<>(dir.resolve("tx.json"), TransactionDto.class));
        int[] calls = {0};
        service.addListener(() -> calls[0]++);
        service.add(new Income(LocalDate.of(2025, 9, 1), new Category("Gaji"), 1L, ""));
        assertEquals(1, calls[0]);
    }
}
