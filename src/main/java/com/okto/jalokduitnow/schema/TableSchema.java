package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;

import java.util.*;

/** Ordered list of custom columns. Keeps names unique and the order numbers consistent. */
public class TableSchema {
    private final List<ColumnDefinition> columns = new ArrayList<>();

    public static String newId() {
        return "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public List<ColumnDefinition> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public List<ColumnDefinition> visibleColumns() {
        return columns.stream().filter(ColumnDefinition::isVisible).toList();
    }

    public Optional<ColumnDefinition> find(String id) {
        return columns.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public ColumnDefinition require(String id) throws InvalidColumnException {
        return find(id).orElseThrow(() -> new InvalidColumnException("Kolom tidak ditemukan"));
    }

    public void add(ColumnDefinition column) throws InvalidColumnException {
        assert column != null;
        if (find(column.getId()).isPresent()) throw new InvalidColumnException("Id kolom sudah dipakai");
        checkNameFree(column.getName(), null);
        columns.add(column);
        reindex();
    }

    public ColumnDefinition remove(String id) throws InvalidColumnException {
        ColumnDefinition col = require(id);
        columns.remove(col);
        reindex();
        return col;
    }

    public void rename(String id, String newName) throws InvalidColumnException {
        ColumnDefinition col = require(id);
        checkNameFree(newName, col);
        col.setName(newName);
    }

    /** Moves a column to a 0-based position (clamped to the valid range). */
    public void move(String id, int newIndex) throws InvalidColumnException {
        ColumnDefinition col = require(id);
        columns.remove(col);
        columns.add(Math.max(0, Math.min(newIndex, columns.size())), col);
        reindex();
    }

    /**
     * Merges form input into a transaction's existing typed custom values.
     * Only visible columns are touched, so values in hidden columns are kept. Blank input clears a value.
     *
     * @param input raw text by column id (as typed by the user)
     */
    public Map<String, Object> applyInput(Map<String, Object> existing, Map<String, String> input)
            throws InvalidTransactionException {
        Map<String, Object> result = new LinkedHashMap<>(existing);
        for (ColumnDefinition col : visibleColumns()) {
            String raw = input.get(col.getId());
            if (raw == null || raw.isBlank()) {
                if (col.isRequired()) throw new InvalidTransactionException(col.getName() + " wajib diisi");
                result.remove(col.getId());
                continue;
            }
            try {
                result.put(col.getId(), col.getType().parseValue(raw));
            } catch (InvalidColumnException e) {
                throw new InvalidTransactionException(col.getName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    /** Throws if the name is blank or used by another column (ignoring case). Used to validate before changing anything. */
    public void validateName(String name, String ignoreColumnId) throws InvalidColumnException {
        checkNameFree(name, ignoreColumnId == null ? null : find(ignoreColumnId).orElse(null));
    }

    private void checkNameFree(String name, ColumnDefinition self) throws InvalidColumnException {
        if (name == null || name.isBlank()) throw new InvalidColumnException("Nama kolom wajib diisi");
        for (ColumnDefinition c : columns) {
            if (c != self && c.getName().equalsIgnoreCase(name.trim())) {
                throw new InvalidColumnException("Nama kolom '" + name.trim() + "' sudah dipakai");
            }
        }
    }

    private void reindex() {
        for (int i = 0; i < columns.size(); i++) columns.get(i).setOrder(i);
    }
}
