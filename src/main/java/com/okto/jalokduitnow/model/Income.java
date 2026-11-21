package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidTransactionException;

import java.time.LocalDate;
import java.util.UUID;

public class Income extends Transaction {
    public Income(LocalDate date, UUID categoryId, long amount)
            throws InvalidTransactionException {
        this(UUID.randomUUID(), date, categoryId, amount);
    }

    public Income(UUID id, LocalDate date, UUID categoryId, long amount)
            throws InvalidTransactionException {
        super(id, date, categoryId, amount);
    }

    @Override public long signedAmount() { return getAmount(); }
    @Override public CategoryType categoryType() { return CategoryType.INCOME; }
}
