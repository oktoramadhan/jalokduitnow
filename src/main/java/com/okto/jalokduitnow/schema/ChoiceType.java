package com.okto.jalokduitnow.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.okto.jalokduitnow.exception.InvalidColumnException;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;

import java.util.ArrayList;
import java.util.List;

/** One value out of a fixed list of options (e.g. Need / Want). Options are fixed at construction. */
public class ChoiceType extends FieldType<String> {
    private final List<String> options;

    @JsonCreator
    public ChoiceType(@JsonProperty("options") List<String> options) throws InvalidColumnException {
        List<String> cleaned = new ArrayList<>();
        for (String o : options == null ? List.<String>of() : options) {
            String trimmed = o == null ? "" : o.trim();
            if (trimmed.isEmpty()) continue;
            if (cleaned.stream().anyMatch(x -> x.equalsIgnoreCase(trimmed))) {
                throw new InvalidColumnException("Opsi ganda: " + trimmed);
            }
            cleaned.add(trimmed);
        }
        if (cleaned.isEmpty()) throw new InvalidColumnException("Pilihan minimal punya 1 opsi");
        this.options = List.copyOf(cleaned);
    }

    @Override public String displayName() { return "Pilihan"; }
    @Override public Class<String> valueClass() { return String.class; }

    @Override
    @JsonProperty("options")
    public List<String> options() { return options; }

    /** Matches ignoring case and returns the option as spelled in the list. */
    @Override
    public String parse(String raw) throws InvalidColumnException {
        return options.stream().filter(o -> o.equalsIgnoreCase(raw)).findFirst()
                .orElseThrow(() -> new InvalidColumnException("'" + raw + "' bukan salah satu opsi: " + options));
    }

    /** Also rejects values that were valid for an older option list. */
    @Override
    public void validate(String value) throws InvalidColumnException {
        if (!options.contains(value)) {
            throw new InvalidColumnException("'" + value + "' bukan salah satu opsi: " + options);
        }
    }

    @Override public String format(String value) { return value; }

    @Override
    public FieldEditor<String> createEditor() {
        List<String> items = new ArrayList<>();
        items.add("");
        items.addAll(options);
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(items));
        box.setValue("");
        return new FieldEditor<>() {
            @Override public Control control() { return box; }
            @Override public String getValue() {
                String v = box.getValue();
                return v == null || v.isEmpty() ? null : v;
            }
            @Override public void setValue(String v) { box.setValue(v == null ? "" : v); }
        };
    }
}
