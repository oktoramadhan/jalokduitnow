package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryResolver;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.storage.CategoryDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.util.*;
import java.util.function.Consumer;

/**
 * Owns all categories. Transactions only hold category ids, so:
 * - rename/recolor/budget changes show up everywhere automatically;
 * - delete and merge move the transactions to another category first. If any transaction uses the
 *   category and no target is given, the call is refused with InvalidCategoryException (no orphans, no silent loss);
 * - archiving hides a category from new entries while old transactions keep it.
 * Names are unique per type (ignoring case). Changes are auto-saved on a background thread.
 */
public class CategoryService implements CategoryResolver {
    public static final String FALLBACK_NAME = "Tanpa Kategori";
    /** Names of the categories created on first run (a user-made category is any other name). */
    public static final List<String> DEFAULT_NAMES =
            List.of("Makan", "Transport", "Belanja", "Hiburan", "Tagihan", "Kesehatan", "Tabungan", "Gaji", "Freelance", "Bonus");
    private static final List<String> PALETTE =
            List.of("#1f9d6b", "#3b82c4", "#f0a030", "#e0583f", "#8a63d2", "#2bb0c4", "#d0508c", "#7d8b99");

    private final AutoSaveService<CategoryDto> autoSave;
    private final Repository<CategoryDto> repository;
    private final FinanceService finance;
    private final Map<UUID, Category> categories = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public CategoryService(Repository<CategoryDto> repository, FinanceService finance) {
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
        this.finance = finance;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void setErrorHandler(Consumer<StorageException> handler) {
        autoSave.setErrorHandler(handler);
    }

    public void flush() {
        autoSave.flush();
    }

    /** Loads categories; a first run gets editable defaults. */
    public void load() throws StorageException {
        List<CategoryDto> saved = repository.findAll();
        categories.clear();
        try {
            if (saved.isEmpty()) {
                seedDefaults();
                save();
                return;
            }
            boolean legacy = false;
            for (CategoryDto dto : saved) {
                Category c = dto.toCategory();
                categories.put(c.getId(), c);
                legacy |= dto.isLegacy();
            }
            if (legacy) save(); // old file with emojis: write it again with icon keys
        } catch (InvalidCategoryException e) {
            throw new StorageException("File kategori rusak: " + e.getMessage(), e);
        }
    }

    private void seedDefaults() throws InvalidCategoryException {
        String[][] expense = {
                {"Makan", "food", "#f0a030"}, {"Transport", "transport", "#3b82c4"}, {"Belanja", "shopping", "#d0508c"},
                {"Hiburan", "entertainment", "#8a63d2"}, {"Tagihan", "bills", "#2bb0c4"}, {"Kesehatan", "health", "#e0583f"},
                {"Tabungan", "savings", "#1f9d6b"}};
        String[][] income = {{"Gaji", "salary", "#1f9d6b"}, {"Freelance", "freelance", "#3b82c4"}, {"Bonus", "gift", "#f0a030"}};
        for (String[] d : expense) put(Category.create(d[0], CategoryType.EXPENSE, d[1], d[2], null));
        for (String[] d : income) put(Category.create(d[0], CategoryType.INCOME, d[1], d[2], null));
    }

    // ---- queries ----

    public List<Category> getAll() {
        return List.copyOf(categories.values());
    }

    /** Categories that can be chosen for new transactions, sorted by name. */
    public List<Category> active(CategoryType type) {
        return categories.values().stream()
                .filter(c -> c.getType() == type && !c.isArchived())
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Optional<Category> find(UUID id) {
        return Optional.ofNullable(categories.get(id));
    }

    public Category require(UUID id) throws InvalidCategoryException {
        Category c = categories.get(id);
        if (c == null) throw new InvalidCategoryException("Kategori tidak ditemukan");
        return c;
    }

    public String nameOf(UUID id) {
        Category c = categories.get(id);
        return c == null ? "?" : c.getName();
    }

    public String displayNameOf(UUID id) {
        return nameOf(id);
    }

    /** Checks that a category may be given to a new or changed transaction of this type. */
    public Category requireUsable(UUID id, CategoryType type) throws InvalidCategoryException {
        Category c = require(id);
        if (c.getType() != type) throw new InvalidCategoryException("Kategori '" + c.getName() + "' bukan kategori " + type.label());
        if (c.isArchived()) throw new InvalidCategoryException("Kategori '" + c.getName() + "' sudah diarsipkan");
        return c;
    }

    // ---- changes ----

    public Category add(String name, CategoryType type, String iconKey, String color, Long monthlyBudget)
            throws InvalidCategoryException {
        Category c = Category.create(name, type, iconKey, color, monthlyBudget);
        checkNameFree(c.getName(), type, null);
        put(c);
        save();
        return c;
    }

    /** Changes name, icon, color and budget at once; nothing changes if any of them is invalid. */
    public void update(UUID id, String name, String iconKey, String color, Long monthlyBudget)
            throws InvalidCategoryException {
        Category c = require(id);
        checkNameFree(name == null ? "" : name.trim(), c.getType(), c);
        c.update(name, iconKey, color, monthlyBudget);
        save();
    }

    public void rename(UUID id, String newName) throws InvalidCategoryException {
        Category c = require(id);
        update(id, newName, c.getIconKey(), c.getColor(), c.getMonthlyBudget());
    }

    public void recolor(UUID id, String color) throws InvalidCategoryException {
        Category c = require(id);
        update(id, c.getName(), c.getIconKey(), color, c.getMonthlyBudget());
    }

    /** @param monthlyBudget Rupiah, or null to remove the budget */
    public void setBudget(UUID id, Long monthlyBudget) throws InvalidCategoryException {
        Category c = require(id);
        update(id, c.getName(), c.getIconKey(), c.getColor(), monthlyBudget);
    }

    /** Archived categories disappear from the pickers but keep their transactions. */
    public void archive(UUID id, boolean archived) throws InvalidCategoryException {
        require(id).setArchived(archived);
        save();
    }

    /** How many transactions use this category (to show before delete/merge). */
    public long usageCount(UUID id) throws InvalidCategoryException {
        require(id);
        return finance.countWithCategory(id);
    }

    /**
     * Deletes a category. If transactions use it, {@code reassignTo} is required and they are moved there first.
     */
    public void delete(UUID id, UUID reassignTo) throws InvalidCategoryException {
        Category source = require(id);
        long used = finance.countWithCategory(id);
        if (used > 0) {
            if (reassignTo == null) {
                throw new InvalidCategoryException(used + " transaksi memakai kategori '" + source.getName()
                        + "'. Pilih kategori tujuan, atau arsipkan saja.");
            }
            checkTarget(source, reassignTo);
            finance.reassignCategory(id, reassignTo);
        }
        categories.remove(id);
        save();
    }

    /** Moves every transaction of {@code sourceId} to {@code targetId}, then removes the source. */
    public void merge(UUID sourceId, UUID targetId) throws InvalidCategoryException {
        if (targetId == null) throw new InvalidCategoryException("Pilih kategori tujuan untuk penggabungan");
        checkTarget(require(sourceId), targetId); // always validated, even if the source has no transactions yet
        delete(sourceId, targetId);
    }

    private Category checkTarget(Category source, UUID targetId) throws InvalidCategoryException {
        if (targetId.equals(source.getId())) throw new InvalidCategoryException("Kategori tujuan harus berbeda");
        Category target = require(targetId);
        if (target.getType() != source.getType()) {
            throw new InvalidCategoryException("Kategori tujuan harus bertipe " + source.getType().label());
        }
        if (target.isArchived()) throw new InvalidCategoryException("Kategori tujuan sudah diarsipkan");
        return target;
    }

    // ---- CategoryResolver (loading / migration) ----

    @Override
    public UUID idFor(CategoryType type, String legacyName) throws InvalidCategoryException {
        String name = legacyName == null ? "" : legacyName.trim();
        Optional<Category> existing = findByName(name, type);
        if (existing.isPresent()) return existing.get().getId();
        Category c = Category.create(name, type, Category.DEFAULT_ICON, PALETTE.get(categories.size() % PALETTE.size()), null);
        put(c);
        save();
        return c.getId();
    }

    @Override
    public UUID resolve(UUID id, CategoryType type) throws InvalidCategoryException {
        Category known = categories.get(id);
        if (known != null && known.getType() == type) return id;
        Optional<Category> fallback = findByName(FALLBACK_NAME, type);
        if (fallback.isPresent()) return fallback.get().getId();
        Category c = Category.create(FALLBACK_NAME, type, Category.DEFAULT_ICON, "#7d8b99", null);
        put(c);
        save();
        return c.getId();
    }

    // ---- internals ----

    private Optional<Category> findByName(String name, CategoryType type) {
        return categories.values().stream()
                .filter(c -> c.getType() == type && c.getName().equalsIgnoreCase(name.trim())).findFirst();
    }

    private void checkNameFree(String name, CategoryType type, Category self) throws InvalidCategoryException {
        if (name.isBlank()) throw new InvalidCategoryException("Nama kategori wajib diisi");
        Optional<Category> clash = findByName(name, type);
        if (clash.isPresent() && clash.get() != self) {
            throw new InvalidCategoryException("Kategori " + type.label() + " '" + name.trim() + "' sudah ada");
        }
    }

    private void put(Category c) {
        categories.put(c.getId(), c);
    }

    private void save() {
        autoSave.save(categories.values().stream().map(CategoryDto::from).toList());
        listeners.forEach(Runnable::run);
    }
}
