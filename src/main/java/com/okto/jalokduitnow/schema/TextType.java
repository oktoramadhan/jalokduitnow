package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import javafx.scene.control.Control;
import javafx.scene.control.TextField;

public class TextType extends FieldType<String> {
    public static final int MAX_LENGTH = 500;

    @Override public String displayName() { return "Teks"; }
    @Override public Class<String> valueClass() { return String.class; }
    @Override public String parse(String raw) { return raw; }
    @Override public String format(String value) { return value; }

    @Override
    public void validate(String value) throws InvalidColumnException {
        if (value.length() > MAX_LENGTH) throw new InvalidColumnException("Teks maksimal " + MAX_LENGTH + " karakter");
    }

    @Override
    public FieldEditor<String> createEditor() {
        TextField field = new TextField();
        return new FieldEditor<>() {
            @Override public Control control() { return field; }
            @Override public String getValue() {
                String t = field.getText();
                return t == null || t.isBlank() ? null : t.trim();
            }
            @Override public void setValue(String v) { field.setText(v == null ? "" : v); }
        };
    }
}
