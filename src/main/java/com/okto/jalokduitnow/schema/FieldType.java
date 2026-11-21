package com.okto.jalokduitnow.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.okto.jalokduitnow.exception.InvalidColumnException;

import java.util.List;

/**
 * Abstraction of "what kind of value does a column hold".
 * Each subclass knows how to parse text into a typed value T, validate it, format it back,
 * and build the right JavaFX editor (polymorphism). Jackson writes the concrete class as
 * {@code "type": "TEXT"} etc., so a schema file round-trips to the same subclass.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextType.class, name = "TEXT"),
        @JsonSubTypes.Type(value = NumberType.class, name = "NUMBER"),
        @JsonSubTypes.Type(value = DateType.class, name = "DATE"),
        @JsonSubTypes.Type(value = ChoiceType.class, name = "CHOICE"),
        @JsonSubTypes.Type(value = BooleanType.class, name = "BOOLEAN")
})
public abstract class FieldType<T> {
    /** Label for the UI (Indonesian). */
    public abstract String displayName();

    /** The Java class of the typed value (used to check values at runtime). */
    public abstract Class<T> valueClass();

    /** Parse user input or stored text. Never called with blank text. */
    public abstract T parse(String raw) throws InvalidColumnException;

    /** Extra rules on a parsed value. Default: everything is valid. */
    public void validate(T value) throws InvalidColumnException {
    }

    /** Canonical text used for storage. */
    public abstract String format(T value);

    /** Text shown to the user. Defaults to the canonical text. */
    public String display(T value) {
        return format(value);
    }

    /** A new JavaFX editor for this type. */
    public abstract FieldEditor<T> createEditor();

    /** Choice options; empty for every other type. */
    public List<String> options() {
        return List.of();
    }

    // ---- helpers for callers that only hold a FieldType<?> and an Object ----

    /** parse + validate. */
    public final T parseValue(String raw) throws InvalidColumnException {
        T value = parse(raw.trim());
        validate(value);
        return value;
    }

    /** Checks the runtime class and the rules, and returns the value as T. */
    public final T check(Object value) throws InvalidColumnException {
        if (!valueClass().isInstance(value)) {
            throw new InvalidColumnException("Nilai bukan bertipe " + displayName());
        }
        T typed = valueClass().cast(value);
        validate(typed);
        return typed;
    }

    /** Canonical storage text of a typed value. */
    public final String formatValue(Object value) throws InvalidColumnException {
        return format(check(value));
    }

    /** User-facing text of a typed value; blank for null. */
    public final String displayValue(Object value) {
        if (value == null) return "";
        try {
            return display(check(value));
        } catch (InvalidColumnException e) {
            return String.valueOf(value);
        }
    }
}
