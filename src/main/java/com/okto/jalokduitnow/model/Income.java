package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidTransactionException;

import java.time.LocalDate;
import java.util.UUID;

public class Income extends Transaction {
    public Income(LocalDate date, Category category, long amount, String description)
            throws InvalidTransactionException {
        this(UUID.randomUUID(), date, category, amount, description);
    }

    public Income(UUID id, LocalDate date, Category category, long amount, String description)
            throws InvalidTransactionException {
        super(id, date, category, amount, description);
    }

    @Override public long signedAmount() { return getAmount(); }
    @Override public String typeLabel() { return "Income"; }
}
