package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.storage.TransactionDto;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** Holds transactions in memory and persists every change through a Repository. */
public class FinanceService {
    private final Repository<TransactionDto> repository;
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
        this.repository = repository;
    }

    public void load() throws StorageException {
        transactions.clear();
        for (TransactionDto dto : repository.findAll()) {
            try {
                transactions.add(dto.toTransaction());
            } catch (InvalidTransactionException e) {
                throw new StorageException("Data rusak: " + dto, e);
            }
        }
        sort();
    }

    public List<Transaction> getAll() {
        return List.copyOf(transactions);
    }

    public void add(Transaction t) throws StorageException {
        transactions.add(t);
        sort();
        persist();
    }

    /** Replaces the stored transaction that has the same id (allows changing type too). */
    public void update(Transaction replacement) throws StorageException {
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

    public boolean delete(UUID id) throws StorageException {
        boolean removed = transactions.removeIf(t -> t.getId().equals(id));
        if (removed) persist();
        return removed;
    }

    public void replaceAll(List<Transaction> imported) throws StorageException {
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

    public MonthSummary summaryFor(YearMonth month) {
        long in = 0, out = 0;
        for (Transaction t : transactions) {
            if (YearMonth.from(t.getDate()).equals(month)) {
                if (t.signedAmount() > 0) in += t.getAmount(); else out += t.getAmount();
            }
        }
        return new MonthSummary(in, out);
    }

    /** Expense per category in one month, biggest first. */
    public Map<String, Long> expenseByCategory(YearMonth month) {
        Map<String, Long> totals = new LinkedHashMap<>();
        transactions.stream()
                .filter(t -> t.signedAmount() < 0 && YearMonth.from(t.getDate()).equals(month))
                .collect(Collectors.groupingBy(t -> t.getCategory().name(), Collectors.summingLong(Transaction::getAmount)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
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

    /** Distinct category names used so far by transactions of the given type label, sorted. */
    public List<String> categoryNames(String typeLabel) {
        return transactions.stream()
                .filter(t -> t.typeLabel().equals(typeLabel))
                .map(t -> t.getCategory().name())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void persist() throws StorageException {
        repository.saveAll(transactions.stream().map(TransactionDto::from).toList());
        listeners.forEach(Runnable::run);
    }
}
