package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.schema.*;
import com.okto.jalokduitnow.storage.ColumnDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.storage.TransactionDto;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Id of the free-text column every new schema starts with (it is not counted as a column the user built). */
    public static final String DEFAULT_COLUMN_ID = TransactionDto.LEGACY_DESCRIPTION_COLUMN;

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
                schema.add(new ColumnDefinition(DEFAULT_COLUMN_ID, "Keterangan", new TextType(), false));
                save();
                return;
            }
            for (ColumnDto dto : saved) schema.add(dto.toColumn());
        } catch (InvalidColumnException e) {
            throw new StorageException("File skema rusak: " + e.getMessage(), e);
        }
    }

    /** Reads the schema from disk again into the same TableSchema object and tells the listeners. */
    public void reload() throws StorageException {
        schema.clear();
        load();
        listeners.forEach(Runnable::run);
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

    /** Checks a column name (blank, duplicate) without changing anything, so a form can validate before it asks questions. */
    public void validateName(String name, String columnId) throws InvalidColumnException {
        schema.validateName(name, columnId);
    }

    /**
     * Renames the column and sets whether it is required, together: if the new name is invalid or already used,
     * nothing changes. (Changing the type or the options is a separate, data-aware step.)
     */
    public void updateColumn(String id, String newName, boolean required) throws InvalidColumnException {
        ColumnDefinition col = schema.require(id);
        schema.rename(id, newName);   // validates: not blank, not a duplicate
        col.setRequired(required);
        save();
    }

    /** How many transactions use each option of a Choice column, in option order (0 for unused ones). */
    public Map<String, Long> optionUsage(String id) throws InvalidColumnException {
        ColumnDefinition col = schema.require(id);
        if (!(col.getType() instanceof ChoiceType choice)) throw new InvalidColumnException("Kolom ini bukan kolom Pilihan");
        Map<String, Long> usage = new LinkedHashMap<>();
        for (String option : choice.options()) usage.put(option, 0L);
        for (var t : finance.getAll()) {
            Object v = t.getCustomValue(id);
            if (v instanceof String s && usage.containsKey(s)) usage.merge(s, 1L, Long::sum);
        }
        return usage;
    }

    /**
     * Replaces the options of a Choice column. {@code valueMapping} says what happens to values of OLD options:
     * a rename maps old to new name, a removal maps old to a replacement option. An option that stays needs no entry.
     * A removed option that is still used and has no replacement is refused, so no value is ever lost silently.
     * (An unused removed option needs no replacement.)
     */
    public void updateChoiceOptions(String id, List<String> newOptions, Map<String, String> valueMapping)
            throws InvalidColumnException {
        ColumnDefinition col = schema.require(id);
        if (!(col.getType() instanceof ChoiceType oldType)) throw new InvalidColumnException("Kolom ini bukan kolom Pilihan");
        ChoiceType newType = new ChoiceType(newOptions);          // validates: at least one option, no duplicates
        Map<String, Long> usage = optionUsage(id);

        Map<String, String> resolved = new HashMap<>();           // old option -> option it becomes
        for (String old : oldType.options()) {
            String wanted = valueMapping.getOrDefault(old, old);
            String match = newType.options().stream().filter(o -> o.equalsIgnoreCase(wanted)).findFirst().orElse(null);
            if (match == null && usage.getOrDefault(old, 0L) > 0) {
                throw new InvalidColumnException("Opsi '" + old + "' dipakai di " + usage.get(old)
                        + " transaksi. Pilih opsi pengganti.");
            }
            if (match != null) resolved.put(old, match);
        }
        col.setType(newType);
        save();
        if (usage.values().stream().anyMatch(n -> n > 0)) {
            finance.transformCustomValues(col, v -> resolved.get(String.valueOf(v)));
        }
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
