package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;

/** One user-defined column. The id is permanent; the name can change. */
public class ColumnDefinition {
    private final String id;
    private String name;
    private int order;
    private boolean visible = true;
    private boolean required;
    private FieldType<?> type;

    public ColumnDefinition(String id, String name, FieldType<?> type, boolean required) throws InvalidColumnException {
        if (id == null || id.isBlank()) throw new InvalidColumnException("Id kolom wajib");
        this.id = id;
        setName(name);
        setType(type);
        this.required = required;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getOrder() { return order; }
    public boolean isVisible() { return visible; }
    public boolean isRequired() { return required; }
    public FieldType<?> getType() { return type; }

    public void setName(String name) throws InvalidColumnException {
        if (name == null || name.isBlank()) throw new InvalidColumnException("Nama kolom wajib diisi");
        this.name = name.trim();
    }

    public void setType(FieldType<?> type) throws InvalidColumnException {
        if (type == null) throw new InvalidColumnException("Tipe kolom wajib");
        this.type = type;
    }

    public void setVisible(boolean visible) { this.visible = visible; }
    public void setRequired(boolean required) { this.required = required; }

    /** Only TableSchema assigns positions. */
    void setOrder(int order) { this.order = order; }
}
