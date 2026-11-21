package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;

import java.util.List;

/**
 * The kinds of column a user can choose from, in the order the UI lists them.
 * Keeps "which FieldType does this label mean" in the schema package, so the UI only picks a kind.
 */
public enum FieldKind {
    TEXT("Teks", "text"),
    NUMBER("Angka", "number"),
    DATE("Tanggal", "calendar"),
    CHOICE("Pilihan", "list"),
    YES_NO("Ya/Tidak", "toggle");

    private final String label;
    private final String iconKey;

    FieldKind(String label, String iconKey) {
        this.label = label;
        this.iconKey = iconKey;
    }

    public String label() { return label; }

    /** Key into the app's icon catalog. */
    public String iconKey() { return iconKey; }

    public boolean needsOptions() { return this == CHOICE; }

    /** A new FieldType of this kind. Only CHOICE uses {@code options} (and requires at least one). */
    public FieldType<?> create(List<String> options) throws InvalidColumnException {
        return switch (this) {
            case TEXT -> new TextType();
            case NUMBER -> new NumberType();
            case DATE -> new DateType();
            case CHOICE -> new ChoiceType(options);
            case YES_NO -> new BooleanType();
        };
    }

    public static FieldKind of(FieldType<?> type) {
        return switch (type) {
            case TextType t -> TEXT;
            case NumberType t -> NUMBER;
            case DateType t -> DATE;
            case ChoiceType t -> CHOICE;
            case BooleanType t -> YES_NO;
            default -> throw new IllegalArgumentException("Tipe kolom tidak dikenal: " + type.getClass());
        };
    }
}
