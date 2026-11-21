package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.storage.TransactionDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Holds transactions in memory and persists every change through a Repository. */
public class FinanceService {
    private final Repository<TransactionDto> repository;
    private final List<Transaction> transactions = new ArrayList<>();

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

    /** Call after editing a transaction's fields through its setters. */
    public void update(Transaction t) throws StorageException {
        assert transactions.contains(t) : "unknown transaction";
        sort();
        persist();
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

    private void persist() throws StorageException {
        repository.saveAll(transactions.stream().map(TransactionDto::from).toList());
    }
}
