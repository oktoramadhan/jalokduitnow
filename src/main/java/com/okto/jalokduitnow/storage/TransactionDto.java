package com.okto.jalokduitnow.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TableSchema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flat, JSON-friendly form of a Transaction (keeps the domain classes free of JSON concerns).
 * The category is saved as its id. Custom values are saved as canonical text keyed by column id.
 * {@code category} (a name) and {@code description} only exist to read files saved by older versions;
 * they are never written.
 */
public record TransactionDto(UUID id, String type, LocalDate date, UUID categoryId, long amount,
                             Map<String, String> custom, Instant createdAt,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String category,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String description) {

    /** Id of the default "Keterangan" column that legacy descriptions are moved into. */
    public static final String LEGACY_DESCRIPTION_COLUMN = "keterangan";

    public static TransactionDto from(Transaction t, TableSchema schema) {
        Map<String, String> text = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : t.getCustomValues().entrySet()) {
            ColumnDefinition col = schema.find(e.getKey()).orElse(null);
            if (col == null) continue; // column no longer exists
            try {
                text.put(e.getKey(), col.getType().formatValue(e.getValue()));
            } catch (InvalidColumnException ex) {
                throw new IllegalStateException("Nilai tidak cocok dengan kolom " + col.getName(), ex);
            }
        }
        return new TransactionDto(t.getId(), t.typeLabel(), t.getDate(), t.getCategoryId(),
                t.getAmount(), text, t.getCreatedAt(), null, null);
    }

    /**
     * Rebuilds the transaction. A saved category id is checked through the resolver (an unknown id becomes the
     * "Tanpa Kategori" fallback); an old-style category name is turned into an id. Values of columns that no
     * longer exist in the schema are dropped (the column was deleted).
     */
    public Transaction toTransaction(TableSchema schema, CategoryResolver categories)
            throws InvalidTransactionException, InvalidColumnException, InvalidCategoryException {
        CategoryType categoryType;
        try {
            categoryType = CategoryType.fromLabel(type);
        } catch (IllegalArgumentException e) {
            throw new InvalidTransactionException("Tipe tidak dikenal: " + type);
        }
        UUID resolvedCategory;
        if (categoryId != null) {
            resolvedCategory = categories.resolve(categoryId, categoryType);
        } else if (category != null && !category.isBlank()) {
            resolvedCategory = categories.idFor(categoryType, category);
        } else {
            throw new InvalidTransactionException("Kategori wajib diisi");
        }

        Transaction t = categoryType == CategoryType.INCOME
                ? new Income(id, date, resolvedCategory, amount)
                : new Expense(id, date, resolvedCategory, amount);

        Map<String, String> text = new LinkedHashMap<>(custom == null ? Map.of() : custom);
        if (description != null && !description.isBlank()) text.putIfAbsent(LEGACY_DESCRIPTION_COLUMN, description.trim());
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : text.entrySet()) {
            ColumnDefinition col = schema.find(e.getKey()).orElse(null);
            if (col != null && e.getValue() != null && !e.getValue().isBlank()) {
                typed.put(e.getKey(), col.getType().parseValue(e.getValue()));
            }
        }
        t.replaceCustomValues(schema, typed);
        // files from before streaks have no creation time: use noon of the entry's own date
        t.setCreatedAt(createdAt != null ? createdAt : date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
        return t;
    }

    /** True if loading this record changed it (old file format or repaired category), so it should be re-saved. */
    public boolean differsFrom(Transaction loaded) {
        return categoryId == null || !categoryId.equals(loaded.getCategoryId()) || category != null || description != null
                || createdAt == null;
    }
}
