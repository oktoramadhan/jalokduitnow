package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.model.*;

import java.time.LocalDate;
import java.util.UUID;

/** Flat, JSON-friendly form of a Transaction (keeps the domain classes free of JSON concerns). */
public record TransactionDto(UUID id, String type, LocalDate date, String category, long amount, String description) {

    public static TransactionDto from(Transaction t) {
        return new TransactionDto(t.getId(), t.typeLabel(), t.getDate(),
                t.getCategory().name(), t.getAmount(), t.getDescription());
    }

    public Transaction toTransaction() throws InvalidTransactionException {
        Category c = new Category(category);
        return switch (type) {
            case "Income" -> new Income(id, date, c, amount, description);
            case "Expense" -> new Expense(id, date, c, amount, description);
            default -> throw new InvalidTransactionException("Tipe tidak dikenal: " + type);
        };
    }
}
