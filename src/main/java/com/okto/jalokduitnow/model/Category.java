package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidCategoryException;

import java.util.UUID;

/**
 * A category is an entity with a stable id. Transactions store the id, never the name,
 * so renaming a category changes it everywhere at once.
 * Name, icon, color and budget are validated together in {@link #update}; the type never changes.
 */
public class Category {
    /** Icon used when none is chosen (also the target for unknown old values). */
    public static final String DEFAULT_ICON = "other";
    public static final int MAX_NAME_LENGTH = 40;

    private final UUID id;
    private final CategoryType type;
    private String name;
    private String iconKey;        // key into the app's icon catalog, e.g. "food"
    private String color;          // "#rrggbb"
    private Long monthlyBudget;    // Rupiah, null = no budget (expense categories only)
    private boolean archived;

    public Category(UUID id, String name, CategoryType type, String iconKey, String color,
                    Long monthlyBudget, boolean archived) throws InvalidCategoryException {
        if (id == null) throw new InvalidCategoryException("Id kategori wajib");
        if (type == null) throw new InvalidCategoryException("Tipe kategori wajib");
        this.id = id;
        this.type = type;
        update(name, iconKey, color, monthlyBudget);
        this.archived = archived;
    }

    /** New category with a fresh random id. */
    public static Category create(String name, CategoryType type, String iconKey, String color, Long monthlyBudget)
            throws InvalidCategoryException {
        return new Category(UUID.randomUUID(), name, type, iconKey, color, monthlyBudget, false);
    }

    /** Changes name, icon, color and budget together; if anything is invalid nothing changes. */
    public void update(String name, String iconKey, String color, Long monthlyBudget) throws InvalidCategoryException {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) throw new InvalidCategoryException("Nama kategori wajib diisi");
        if (cleanName.length() > MAX_NAME_LENGTH) {
            throw new InvalidCategoryException("Nama kategori maksimal " + MAX_NAME_LENGTH + " karakter");
        }
        String cleanIcon = iconKey == null || iconKey.isBlank() ? DEFAULT_ICON : iconKey.trim();
        if (!cleanIcon.matches("[a-z0-9_]{1,32}")) throw new InvalidCategoryException("Kunci ikon tidak valid");
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new InvalidCategoryException("Warna harus berformat #rrggbb");
        }
        if (monthlyBudget != null) {
            if (type == CategoryType.INCOME) throw new InvalidCategoryException("Budget hanya untuk kategori Expense");
            if (monthlyBudget <= 0) throw new InvalidCategoryException("Budget harus lebih dari 0");
        }
        this.name = cleanName;
        this.iconKey = cleanIcon;
        this.color = color.toLowerCase();
        this.monthlyBudget = monthlyBudget;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public CategoryType getType() { return type; }
    public String getIconKey() { return iconKey; }
    public String getColor() { return color; }
    public Long getMonthlyBudget() { return monthlyBudget; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    /** Text for lists and labels (the icon is drawn separately). */
    public String displayName() { return name; }

    @Override
    public String toString() {
        return displayName(); // also what ChoiceDialog/ComboBox show by default
    }
}
