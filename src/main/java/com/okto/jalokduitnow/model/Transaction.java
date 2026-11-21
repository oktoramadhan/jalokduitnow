package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidTransactionException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Abstraction: every money movement has a date, category, amount and description.
 * Subclasses decide how they affect the balance (polymorphism via {@link #signedAmount()}).
 * Amounts are Rupiah stored as long, never double.
 */
public abstract class Transaction {
    private final UUID id;
    private LocalDate date;
    private Category category;
    private long amount;
    private String description;

    protected Transaction(UUID id, LocalDate date, Category category, long amount, String description)
            throws InvalidTransactionException {
        this.id = id;
        setDate(date);
        setCategory(category);
        setAmount(amount);
        setDescription(description);
    }

    /** Positive for income, negative for expense. */
    public abstract long signedAmount();

    /** Label shown in the UI / used in Excel ("Income" or "Expense"). */
    public abstract String typeLabel();

    public UUID getId() { return id; }
    public LocalDate getDate() { return date; }
    public Category getCategory() { return category; }
    public long getAmount() { return amount; }
    public String getDescription() { return description; }

    public void setDate(LocalDate date) throws InvalidTransactionException {
        if (date == null) throw new InvalidTransactionException("Tanggal wajib diisi");
        this.date = date;
    }

    public void setCategory(Category category) throws InvalidTransactionException {
        if (category == null) throw new InvalidTransactionException("Kategori wajib diisi");
        this.category = category;
    }

    public void setAmount(long amount) throws InvalidTransactionException {
        if (amount <= 0) throw new InvalidTransactionException("Jumlah harus lebih dari 0");
        this.amount = amount;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    @Override
    public String toString() {
        return typeLabel() + "[" + date + ", " + category.name() + ", " + amount + "]";
    }
}
