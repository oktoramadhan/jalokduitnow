package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TableSchema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstraction: every money movement has a date, a category id and an amount (the fixed core columns).
 * It stores the category's id, not its name, so renaming a category needs no change here.
 * Anything else lives in user-defined custom columns, stored by column id.
 * Subclasses decide how they affect the balance (polymorphism via {@link #signedAmount()}).
 * Amounts are Rupiah stored as long, never double.
 */
public abstract class Transaction {
    private final UUID id;
    private LocalDate date;
    private UUID categoryId;
    private long amount;
    private Instant createdAt; // when the entry was added (set once by FinanceService); drives streaks and XP
    private final Map<String, Object> customValues = new LinkedHashMap<>();

    protected Transaction(UUID id, LocalDate date, UUID categoryId, long amount)
            throws InvalidTransactionException {
        this.id = id;
        setDate(date);
        setCategoryId(categoryId);
        setAmount(amount);
    }

    /** Positive for income, negative for expense. */
    public abstract long signedAmount();

    /** Income or Expense; decides which categories this transaction may use. */
    public abstract CategoryType categoryType();

    /** Label shown in the UI and saved in files ("Income" or "Expense"). */
    public String typeLabel() { return categoryType().label(); }

    public UUID getId() { return id; }

    /** When this entry was created, or null if it has not been added yet. */
    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) throws InvalidTransactionException {
        if (createdAt == null) throw new InvalidTransactionException("Waktu pembuatan wajib diisi");
        this.createdAt = createdAt;
    }

    public LocalDate getDate() { return date; }
    public UUID getCategoryId() { return categoryId; }
    public long getAmount() { return amount; }
    /** Read-only view of custom column values (column id -> typed value). */
    public Map<String, Object> getCustomValues() { return Collections.unmodifiableMap(customValues); }

    public Object getCustomValue(String columnId) { return customValues.get(columnId); }

    public void setDate(LocalDate date) throws InvalidTransactionException {
        if (date == null) throw new InvalidTransactionException("Tanggal wajib diisi");
        this.date = date;
    }

    public void setCategoryId(UUID categoryId) throws InvalidTransactionException {
        if (categoryId == null) throw new InvalidTransactionException("Kategori wajib diisi");
        this.categoryId = categoryId;
    }

    public void setAmount(long amount) throws InvalidTransactionException {
        if (amount <= 0) throw new InvalidTransactionException("Jumlah harus lebih dari 0");
        this.amount = amount;
    }

    /**
     * Sets one custom value after checking it against the column's type; null removes it.
     * Encapsulation: the map can only be changed through validating methods.
     */
    public void setCustomValue(ColumnDefinition column, Object value) throws InvalidColumnException {
        if (value == null) {
            customValues.remove(column.getId());
            return;
        }
        customValues.put(column.getId(), column.getType().check(value));
    }

    public void removeCustomValue(String columnId) {
        customValues.remove(columnId);
    }

    /** Replaces all custom values. Every value is validated first; on any error nothing changes. */
    public void replaceCustomValues(TableSchema schema, Map<String, Object> values) throws InvalidColumnException {
        Map<String, Object> checked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            ColumnDefinition col = schema.require(e.getKey());
            checked.put(e.getKey(), col.getType().check(e.getValue()));
        }
        customValues.clear();
        customValues.putAll(checked);
    }

    @Override
    public String toString() {
        return typeLabel() + "[" + date + ", " + categoryId + ", " + amount + "]";
    }
}
