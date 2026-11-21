package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.schema.*;
import com.okto.jalokduitnow.storage.ColumnDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.storage.TransactionDto;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the table schema and keeps it in sync with the stored transactions.
 * Rule: deleting a column or changing its type never loses data silently. Callers ask for a
 * preview first; a destructive call without {@code confirmed = true} is refused.
 * Changes are auto-saved on a background thread.
 */
public class SchemaService {
    /** What a type change would do to existing values. */
    public record TypeChangeImpact(long total, long incompatible) {}

    private final Repository<ColumnDto> repository;
    private final AutoSaveService<ColumnDto> autoSave;
    private final FinanceService finance;
    private final TableSchema schema = new TableSchema();
    private final List<Runnable> listeners = new ArrayList<>();

    public SchemaService(Repository<ColumnDto> repository, FinanceService finance) {
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
        this.finance = finance;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void setErrorHandler(Consumer<StorageException> handler) {
        autoSave.setErrorHandler(handler);
    }

    public void flush() {
        autoSave.flush();
    }

    /** Loads the schema; a first run gets a default free-text "Keterangan" column. */
    public void load() throws StorageException {
        List<ColumnDto> saved = repository.findAll();
        try {
            if (saved.isEmpty()) {
                schema.add(new ColumnDefinition(TransactionDto.LEGACY_DESCRIPTION_COLUMN, "Keterangan", new TextType(), false));
                save();
                return;
            }
            for (ColumnDto dto : saved) schema.add(dto.toColumn());
        } catch (InvalidColumnException e) {
            throw new StorageException("File skema rusak: " + e.getMessage(), e);
        }
    }

    public TableSchema getSchema() {
        return schema;
    }

    public ColumnDefinition addColumn(String name, FieldType<?> type, boolean required) throws InvalidColumnException {
        ColumnDefinition col = new ColumnDefinition(TableSchema.newId(), name, type, required);
        schema.add(col);
        save();
        return col;
    }

    public void rename(String id, String newName) throws InvalidColumnException {
        schema.rename(id, newName);
        save();
    }

    public void move(String id, int newIndex) throws InvalidColumnException {
        schema.move(id, newIndex);
        save();
    }

    /** Hiding is safe: values stay stored and come back when the column is shown again. */
    public void setVisible(String id, boolean visible) throws InvalidColumnException {
        schema.require(id).setVisible(visible);
        save();
    }

    public void setRequired(String id, boolean required) throws InvalidColumnException {
        schema.require(id).setRequired(required);
        save();
    }

    /** Number of transactions whose values would be deleted along with the column. */
    public long previewDelete(String id) throws InvalidColumnException {
        schema.require(id);
        return finance.countWithValue(id);
    }

    public void deleteColumn(String id, boolean confirmed) throws InvalidColumnException {
        long affected = previewDelete(id);
        if (affected > 0 && !confirmed) {
            throw new InvalidColumnException("Menghapus kolom ini akan menghapus nilai pada " + affected
                    + " transaksi. Konfirmasi diperlukan.");
        }
        schema.remove(id);
        save();
        if (affected > 0) finance.removeCustomValues(id);
    }

    public TypeChangeImpact previewTypeChange(String id, FieldType<?> newType) throws InvalidColumnException {
        FieldType<?> oldType = schema.require(id).getType();
        long total = 0, bad = 0;
        for (var t : finance.getAll()) {
            Object v = t.getCustomValue(id);
            if (v == null) continue;
            total++;
            if (convert(oldType, newType, v) == null) bad++;
        }
        return new TypeChangeImpact(total, bad);
    }

    /**
     * Changes the type (also used to edit Choice options). Values that convert are rewritten as the
     * new type; values that cannot convert are removed, and only if {@code confirmed}.
     */
    public void changeType(String id, FieldType<?> newType, boolean confirmed) throws InvalidColumnException {
        TypeChangeImpact impact = previewTypeChange(id, newType);
        if (impact.incompatible() > 0 && !confirmed) {
            throw new InvalidColumnException(impact.incompatible() + " dari " + impact.total()
                    + " nilai tidak cocok dengan tipe baru dan akan dihapus. Konfirmasi diperlukan.");
        }
        ColumnDefinition col = schema.require(id);
        FieldType<?> oldType = col.getType();
        col.setType(newType);
        save();
        if (impact.total() > 0) finance.transformCustomValues(col, v -> convert(oldType, newType, v));
    }

    /** The value re-read as the new type (via its text form), or null if it does not fit. */
    private static Object convert(FieldType<?> from, FieldType<?> to, Object value) {
        try {
            return to.parseValue(from.formatValue(value));
        } catch (InvalidColumnException e) {
            return null;
        }
    }

    private void save() {
        autoSave.save(schema.getColumns().stream().map(ColumnDto::from).toList());
        listeners.forEach(Runnable::run);
    }
}
