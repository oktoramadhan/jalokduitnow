package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import javafx.scene.control.Control;
import javafx.scene.control.TextField;

import java.text.NumberFormat;
import java.util.Locale;

/** Whole numbers only (long). Dots and spaces are accepted as thousand separators: "25.000". */
public class NumberType extends FieldType<Long> {
    @Override public String displayName() { return "Angka"; }
    @Override public Class<Long> valueClass() { return Long.class; }

    @Override
    public Long parse(String raw) throws InvalidColumnException {
        String cleaned = raw.replace(".", "").replace(" ", "");
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            throw new InvalidColumnException("'" + raw + "' bukan angka bulat");
        }
    }

    @Override public String format(Long value) { return Long.toString(value); }

    @Override
    public String display(Long value) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(value);
    }

    @Override
    public FieldEditor<Long> createEditor() {
        TextField field = new TextField();
        field.setPromptText("angka");
        return new FieldEditor<>() {
            @Override public Control control() { return field; }
            @Override public Long getValue() throws InvalidColumnException {
                String t = field.getText();
                return t == null || t.isBlank() ? null : parseValue(t);
            }
            @Override public void setValue(Long v) { field.setText(v == null ? "" : format(v)); }
        };
    }
}
