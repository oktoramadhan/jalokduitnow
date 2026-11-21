package com.okto.jalokduitnow.model;

/** Whether a category (and the transactions using it) is money in or money out. */
public enum CategoryType {
    INCOME("Income"),
    EXPENSE("Expense");

    private final String label;

    CategoryType(String label) {
        this.label = label;
    }

    /** Text used in the UI and in saved files. */
    public String label() {
        return label;
    }

    public static CategoryType fromLabel(String label) {
        for (CategoryType t : values()) if (t.label.equalsIgnoreCase(label)) return t;
        throw new IllegalArgumentException("Tipe tidak dikenal: " + label);
    }
}
