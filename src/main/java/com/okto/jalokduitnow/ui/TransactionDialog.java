package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.service.CategoryService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Add/edit form: fixed core fields plus one field per visible custom column (built from the schema).
 * Validation errors are shown inline and keep the dialog open.
 * Custom fields are plain text for now; Stage 5 swaps in typed editors.
 */
public final class TransactionDialog {
    private TransactionDialog() {}

    /** @param existing transaction to edit, or null to create a new one */
    public static Optional<Transaction> show(CategoryService categories, TableSchema schema, Transaction existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Tambah Transaksi" : "Ubah Transaksi");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        DatePicker date = new DatePicker(existing == null ? LocalDate.now() : existing.getDate());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("Expense", "Income"));
        type.setValue(existing == null ? "Expense" : existing.typeLabel());
        ComboBox<Category> category = new ComboBox<>();
        category.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.displayName(); }
            @Override public Category fromString(String s) { return null; }
        });
        // active categories of the chosen type; an archived category stays selectable if this transaction already uses it
        Runnable fillCategories = () -> {
            CategoryType ct = CategoryType.fromLabel(type.getValue());
            List<Category> items = new ArrayList<>(categories.active(ct));
            if (existing != null) {
                categories.find(existing.getCategoryId())
                        .filter(c -> c.getType() == ct && !items.contains(c)).ifPresent(items::add);
            }
            Category keep = category.getValue();
            category.setItems(FXCollections.observableArrayList(items));
            if (keep != null && items.contains(keep)) category.setValue(keep);
        };
        type.setOnAction(e -> { category.setValue(null); fillCategories.run(); });
        fillCategories.run();
        if (existing != null) categories.find(existing.getCategoryId()).ifPresent(category::setValue);
        TextField amount = new TextField(existing == null ? "" : String.valueOf(existing.getAmount()));
        amount.setPromptText("contoh: 25000");

        Label error = new Label();
        error.getStyleClass().add("error-label");
        Theme.styleDialog(dialog.getDialogPane());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Tanggal"), date);
        grid.addRow(1, new Label("Tipe"), type);
        grid.addRow(2, new Label("Kategori"), category);
        grid.addRow(3, new Label("Jumlah (Rp)"), amount);

        // one field per visible custom column
        Map<String, TextField> customFields = new LinkedHashMap<>();
        int row = 4;
        for (ColumnDefinition col : schema.visibleColumns()) {
            TextField field = new TextField();
            if (existing != null) field.setText(col.getType().displayValue(existing.getCustomValue(col.getId())));
            field.setPromptText(col.getType().displayName()
                    + (col.getType().options().isEmpty() ? "" : ": " + String.join(" / ", col.getType().options())));
            customFields.put(col.getId(), field);
            grid.addRow(row++, new Label(col.getName() + (col.isRequired() ? " *" : "")), field);
        }
        grid.add(error, 0, row, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Transaction[] built = new Transaction[1];
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.getStyleClass().add("primary");
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                Map<String, String> input = new LinkedHashMap<>();
                customFields.forEach((id, f) -> input.put(id, f.getText()));
                Map<String, Object> merged = schema.applyInput(
                        existing == null ? Map.of() : existing.getCustomValues(), input);
                built[0] = build(existing, date.getValue(), type.getValue(),
                        category.getValue(), amount.getText(), categories, schema, merged);
            } catch (InvalidTransactionException | InvalidColumnException | InvalidCategoryException ex) {
                error.setText(ex.getMessage());
                ev.consume(); // keep dialog open
            }
        });

        return dialog.showAndWait().filter(b -> b == ButtonType.OK).map(b -> built[0]);
    }

    private static Transaction build(Transaction existing, LocalDate date, String type, Category category,
                                     String amountText, CategoryService categories, TableSchema schema,
                                     Map<String, Object> custom)
            throws InvalidTransactionException, InvalidColumnException, InvalidCategoryException {
        if (category == null) throw new InvalidTransactionException("Kategori wajib dipilih");
        CategoryType categoryType = CategoryType.fromLabel(type);
        // a transaction may keep an archived category it already has, but cannot get a new archived one
        boolean unchanged = existing != null && existing.getCategoryId().equals(category.getId());
        if (!unchanged) categories.requireUsable(category.getId(), categoryType);
        else if (category.getType() != categoryType) categories.requireUsable(category.getId(), categoryType);
        String digits = amountText == null ? "" : amountText.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) throw new InvalidTransactionException("Jumlah wajib diisi");
        long amount;
        try {
            amount = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new InvalidTransactionException("Jumlah terlalu besar");
        }
        UUID id = existing == null ? UUID.randomUUID() : existing.getId();
        Transaction t = categoryType == CategoryType.INCOME
                ? new Income(id, date, category.getId(), amount)
                : new Expense(id, date, category.getId(), amount);
        t.replaceCustomValues(schema, custom);
        return t;
    }
}
