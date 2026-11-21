package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.Transaction;

import java.util.List;

/** Outcome of an Excel import: parsed data plus warnings for rows that were skipped. */
public record ImportResult(List<Transaction> transactions,
                           List<Category> incomeCategories,
                           List<Category> expenseCategories,
                           List<String> warnings) {
}
