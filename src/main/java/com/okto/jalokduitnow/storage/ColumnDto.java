package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.FieldType;

/**
 * JSON form of a ColumnDefinition. Jackson stores the FieldType polymorphically
 * ({@code "type": {"type": "CHOICE", "options": [...]}}). A column's position is its position in the saved list.
 */
public record ColumnDto(String id, String name, boolean visible, boolean required, FieldType<?> type) {

    public static ColumnDto from(ColumnDefinition c) {
        return new ColumnDto(c.getId(), c.getName(), c.isVisible(), c.isRequired(), c.getType());
    }

    public ColumnDefinition toColumn() throws InvalidColumnException {
        ColumnDefinition col = new ColumnDefinition(id, name, type, required);
        col.setVisible(visible);
        return col;
    }
}
