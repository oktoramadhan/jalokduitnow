package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** Stored as ISO (yyyy-MM-dd); accepts ISO or dd/MM/yyyy as input. Any year is valid: there is no minimum or maximum here. */
public class DateType extends FieldType<LocalDate> {
    // strict: 31/02 is an error, not 28/02
    private static final DateTimeFormatter INPUT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SHOWN =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"));

    @Override public String displayName() { return "Tanggal"; }
    @Override public Class<LocalDate> valueClass() { return LocalDate.class; }

    @Override
    public LocalDate parse(String raw) throws InvalidColumnException {
        try {
            return raw.contains("/") ? LocalDate.parse(raw, INPUT) : LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new InvalidColumnException("'" + raw + "' bukan tanggal valid (gunakan dd/MM/yyyy)");
        }
    }

    @Override public String format(LocalDate value) { return value.toString(); }
    @Override public String display(LocalDate value) { return value.format(SHOWN); }

    @Override
    public FieldEditor<LocalDate> createEditor() {
        DatePicker picker = new DatePicker();
        return new FieldEditor<>() {
            @Override public Control control() { return picker; }
            @Override public LocalDate getValue() { return picker.getValue(); }
            @Override public void setValue(LocalDate v) { picker.setValue(v); }
        };
    }
}
