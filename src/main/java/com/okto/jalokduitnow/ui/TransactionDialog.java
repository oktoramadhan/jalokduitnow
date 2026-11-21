package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.service.FinanceService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Add/edit form. Validation errors from the model are shown inline and keep the dialog open. */
public final class TransactionDialog {
    private TransactionDialog() {}

    /** @param existing transaction to edit, or null to create a new one */
    public static Optional<Transaction> show(FinanceService service, Transaction existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Tambah Transaksi" : "Ubah Transaksi");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        DatePicker date = new DatePicker(existing == null ? LocalDate.now() : existing.getDate());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("Expense", "Income"));
        type.setValue(existing == null ? "Expense" : existing.typeLabel());
        ComboBox<String> category = new ComboBox<>();
        category.setEditable(true); // allow typing a new category
        Runnable fillCategories = () -> {
            String current = category.getEditor().getText();
            category.setItems(FXCollections.observableArrayList(service.categoryNames(type.getValue())));
            category.getEditor().setText(current);
        };
        fillCategories.run();
        type.setOnAction(e -> fillCategories.run());
        if (existing != null) category.getEditor().setText(existing.getCategory().name());
        TextField amount = new TextField(existing == null ? "" : String.valueOf(existing.getAmount()));
        amount.setPromptText("contoh: 25000");
        TextField description = new TextField(existing == null ? "" : existing.getDescription());
        Label error = new Label();
        error.setStyle("-fx-text-fill: #c0392b;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Tanggal"), date);
        grid.addRow(1, new Label("Tipe"), type);
        grid.addRow(2, new Label("Kategori"), category);
        grid.addRow(3, new Label("Jumlah (Rp)"), amount);
        grid.addRow(4, new Label("Keterangan"), description);
        grid.add(error, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Transaction[] built = new Transaction[1];
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                built[0] = build(existing, date.getValue(), type.getValue(),
                        category.getEditor().getText(), amount.getText(), description.getText());
            } catch (InvalidTransactionException | IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                ev.consume(); // keep dialog open
            }
        });

        return dialog.showAndWait().filter(b -> b == ButtonType.OK).map(b -> built[0]);
    }

    private static Transaction build(Transaction existing, LocalDate date, String type, String category,
                                     String amountText, String description) throws InvalidTransactionException {
        String digits = amountText == null ? "" : amountText.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) throw new InvalidTransactionException("Jumlah wajib diisi");
        long amount;
        try {
            amount = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new InvalidTransactionException("Jumlah terlalu besar");
        }
        UUID id = existing == null ? UUID.randomUUID() : existing.getId();
        Category c = new Category(category); // throws IllegalArgumentException if blank
        return "Income".equals(type)
                ? new Income(id, date, c, amount, description)
                : new Expense(id, date, c, amount, description);
    }
}
