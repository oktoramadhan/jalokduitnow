package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonRepositoryTest {
    @Test
    void roundTripsTransactions(@TempDir Path dir) throws Exception {
        var repo = new JsonRepository<>(dir.resolve("tx.json"), TransactionDto.class);
        assertTrue(repo.findAll().isEmpty());

        Transaction t = new Expense(LocalDate.of(2025, 8, 3), new Category("Makan"), 25_000L, "nasi");
        repo.saveAll(List.of(TransactionDto.from(t)));

        Transaction back = repo.findAll().get(0).toTransaction();
        assertEquals(t.getId(), back.getId());
        assertEquals(-25_000L, back.signedAmount());
        assertEquals(LocalDate.of(2025, 8, 3), back.getDate());
    }
}
