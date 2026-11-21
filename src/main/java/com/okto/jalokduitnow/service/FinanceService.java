package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.CategoryResolver;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.storage.TransactionDto;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/** Holds transactions in memory; every change is auto-saved on a background thread. */
public class FinanceService {
    private final Repository<TransactionDto> repository;
    private final AutoSaveService<TransactionDto> autoSave;
    private final Clock clock;
    private GameEventBus eventBus; // optional: TransactionAdded is published here
    private TableSchema schema = new TableSchema(); // needed to turn custom values into text; set by load()
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    /** Income and expense totals for one period. */
    public record MonthSummary(long income, long expense) {
        public long balance() { return income - expense; }
    }

    /** Views register here to refresh after any change (called on the thread that changed the data). */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public FinanceService(Repository<TransactionDto> repository) {
        this(repository, Clock.systemDefaultZone());
    }

    /** The clock stamps the creation time of new transactions (a fixed clock makes tests deterministic). */
    public FinanceService(Repository<TransactionDto> repository, Clock clock) {
        this.clock = clock;
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
    }

    /** Where save failures go (called on the save thread; hop to the FX thread in the UI). */
    public void setErrorHandler(java.util.function.Consumer<StorageException> handler) {
        autoSave.setErrorHandler(handler);
    }

    /** Writes any pending save now. Call before exiting (and in tests before re-reading the file). */
    public void flush() {
        autoSave.flush();
    }

    /**
     * Loads from disk. The schema must already be loaded (it tells how to read custom values) and the
     * resolver must know the categories. Files from older versions, which saved category names, are
     * migrated to category ids and saved again in the new format.
     */
    public void load(TableSchema schema, CategoryResolver categories) throws StorageException {
        this.schema = schema;
        transactions.clear();
        boolean changed = false;
        for (TransactionDto dto : repository.findAll()) {
            try {
                Transaction t = dto.toTransaction(schema, categories);
                changed |= dto.differsFrom(t);
                transactions.add(t);
            } catch (InvalidTransactionException | InvalidColumnException | InvalidCategoryException e) {
                throw new StorageException("Data rusak: " + e.getMessage(), e);
            }
        }
        sort();
        if (changed) persist();
    }

    public List<Transaction> getAll() {
        return List.copyOf(transactions);
    }

    /** Where "transaction added" events go (the game listens). Optional. */
    public void setEventBus(GameEventBus bus) {
        this.eventBus = bus;
    }

    /** Adds a transaction, stamps its creation time if it has none, saves, and announces it. */
    public void add(Transaction t) {
        if (t.getCreatedAt() == null) {
            try {
                t.setCreatedAt(Instant.now(clock));
            } catch (com.okto.jalokduitnow.exception.InvalidTransactionException e) {
                throw new IllegalStateException(e); // cannot happen: now() is never null
            }
        }
        transactions.add(t);
        sort();
        persist();
        if (eventBus != null) eventBus.publish(new GameEvent.TransactionAdded(t)); // add only, never edit/delete
    }

    /** Replaces the stored transaction that has the same id (allows changing type too). */
    public void update(Transaction replacement) {
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).getId().equals(replacement.getId())) {
                transactions.set(i, replacement);
                sort();
                persist();
                return;
            }
        }
        throw new IllegalArgumentException("Transaksi tidak ditemukan: " + replacement.getId());
    }

    public boolean delete(UUID id) {
        boolean removed = transactions.removeIf(t -> t.getId().equals(id));
        if (removed) persist();
        return removed;
    }

    public void replaceAll(List<Transaction> imported) {
        transactions.clear();
        transactions.addAll(imported);
        sort();
        persist();
    }

    public long totalIncome() {
        return transactions.stream().filter(t -> t.signedAmount() > 0).mapToLong(Transaction::getAmount).sum();
    }

    public long totalExpense() {
        return transactions.stream().filter(t -> t.signedAmount() < 0).mapToLong(Transaction::getAmount).sum();
    }

    public long balance() {
        return transactions.stream().mapToLong(Transaction::signedAmount).sum();
    }

    private void sort() {
        transactions.sort(Comparator.comparing(Transaction::getDate).reversed());
    }

    /** How many transactions have a value in this custom column (used to warn before data loss). */
    public long countWithValue(String columnId) {
        return transactions.stream().filter(t -> t.getCustomValue(columnId) != null).count();
    }

    /**
     * Rewrites one custom column's values on every transaction that has one.
     * The function gets the old value and returns the new one, or null to remove it.
     * Values that fail validation against the column are removed. Saved once.
     */
    public void transformCustomValues(ColumnDefinition column, UnaryOperator<Object> fn) {
        for (Transaction t : transactions) {
            Object old = t.getCustomValue(column.getId());
            if (old == null) continue;
            try {
                t.setCustomValue(column, fn.apply(old));
            } catch (InvalidColumnException e) {
                t.removeCustomValue(column.getId());
            }
        }
        persist();
    }

    /** Removes one column's values from every transaction (used after the column is deleted). */
    public void removeCustomValues(String columnId) {
        transactions.forEach(t -> t.removeCustomValue(columnId));
        persist();
    }

    public MonthSummary summaryFor(YearMonth month) {
        long in = 0, out = 0;
        for (Transaction t : transactions) {
            if (YearMonth.from(t.getDate()).equals(month)) {
                if (t.signedAmount() > 0) in += t.getAmount(); else out += t.getAmount();
            }
        }
        return new MonthSummary(in, out);
    }

    /** Expense per category id in one month, biggest first. */
    public Map<UUID, Long> expenseByCategory(YearMonth month) {
        Map<UUID, Long> totals = new LinkedHashMap<>();
        transactions.stream()
                .filter(t -> t.signedAmount() < 0 && YearMonth.from(t.getDate()).equals(month))
                .collect(Collectors.groupingBy(Transaction::getCategoryId, Collectors.summingLong(Transaction::getAmount)))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .forEach(e -> totals.put(e.getKey(), e.getValue()));
        return totals;
    }

    /** Every month that has data, oldest first. */
    public SortedMap<YearMonth, MonthSummary> summaryByMonth() {
        SortedSet<YearMonth> months = new TreeSet<>();
        transactions.forEach(t -> months.add(YearMonth.from(t.getDate())));
        SortedMap<YearMonth, MonthSummary> result = new TreeMap<>();
        months.forEach(m -> result.put(m, summaryFor(m)));
        return result;
    }

    /** How many transactions use this category. */
    public long countWithCategory(UUID categoryId) {
        return transactions.stream().filter(t -> t.getCategoryId().equals(categoryId)).count();
    }

    /** Moves every transaction from one category to another. Saved once. */
    public void reassignCategory(UUID from, UUID to) {
        try {
            for (Transaction t : transactions) {
                if (t.getCategoryId().equals(from)) t.setCategoryId(to);
            }
        } catch (InvalidTransactionException e) {
            throw new IllegalArgumentException("Kategori tujuan wajib diisi", e); // callers pass a checked, non-null id
        }
        persist();
    }

    private void persist() {
        // snapshot on the calling thread, write on the auto-save thread
        autoSave.save(transactions.stream().map(t -> TransactionDto.from(t, schema)).toList());
        listeners.forEach(Runnable::run);
    }
}
