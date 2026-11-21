package com.okto.jalokduitnow.model;

import java.util.List;
import java.util.UUID;

/** A savings target linked to a category (e.g. "Tabungan"). Progress is computed from the transactions in that category. */
public record SavingsGoal(UUID id, String name, long targetAmount, UUID categoryId) {
    /** Saved so far and how far that is. */
    public record Progress(long saved, long target, int percent) {}

    public SavingsGoal {
        if (id == null || categoryId == null) throw new IllegalArgumentException("id dan kategori wajib");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nama target wajib diisi");
        if (targetAmount <= 0) throw new IllegalArgumentException("Target harus lebih dari 0");
        name = name.trim();
    }

    /** Percent is capped at 100. */
    public Progress progress(List<Transaction> transactions) {
        long saved = transactions.stream().filter(t -> t.getCategoryId().equals(categoryId))
                .mapToLong(Transaction::getAmount).sum();
        int percent = (int) Math.min(100, saved * 100 / targetAmount);
        return new Progress(saved, targetAmount, percent);
    }
}
