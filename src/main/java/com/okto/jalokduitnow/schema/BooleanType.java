package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;

/** Yes/No column. Stored as "true"/"false". The editor has a blank choice so "not answered" is possible. */
public class BooleanType extends FieldType<Boolean> {
    @Override public String displayName() { return "Ya/Tidak"; }
    @Override public Class<Boolean> valueClass() { return Boolean.class; }

    @Override
    public Boolean parse(String raw) throws InvalidColumnException {
        return switch (raw.toLowerCase()) {
            case "true", "ya", "yes", "y" -> true;
            case "false", "tidak", "no", "n" -> false;
            default -> throw new InvalidColumnException("'" + raw + "' harus Ya atau Tidak");
        };
    }

    @Override public String format(Boolean value) { return value.toString(); }
    @Override public String display(Boolean value) { return value ? "Ya" : "Tidak"; }

    @Override
    public FieldEditor<Boolean> createEditor() {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList("", "Ya", "Tidak"));
        box.setValue("");
        return new FieldEditor<>() {
            @Override public Control control() { return box; }
            @Override public Boolean getValue() {
                String v = box.getValue();
                return v == null || v.isEmpty() ? null : "Ya".equals(v);
            }
            @Override public void setValue(Boolean v) { box.setValue(v == null ? "" : v ? "Ya" : "Tidak"); }
        };
    }
}
