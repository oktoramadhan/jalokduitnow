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
}
