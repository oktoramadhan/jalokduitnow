package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidGoalException;
import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.model.SavingsGoal;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.GameService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

/** Create or edit a savings goal. The game service validates; errors are shown inline and keep the dialog open. */
public final class SavingsGoalDialog {
    private SavingsGoalDialog() {}

    /** @param existing the goal to edit, or null to create a new one */
    public static void show(GameService game, CategoryService categories, SavingsGoal existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Target Tabungan Baru" : "Ubah Target Tabungan");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Theme.styleDialog(dialog.getDialogPane());

        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText("contoh: Laptop baru");
        TextField target = new TextField(existing == null ? "" : String.valueOf(existing.targetAmount()));
        target.setPromptText("contoh: 5000000");
        ComboBox<Category> category = new ComboBox<>(
                FXCollections.observableArrayList(categories.active(CategoryType.EXPENSE)));
        category.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) { return null; }
        });
        if (existing != null) categories.find(existing.categoryId()).ifPresent(category::setValue);
        else category.getItems().stream().filter(c -> c.getName().equalsIgnoreCase("Tabungan")).findFirst().ifPresent(category::setValue);
        for (Control c : new Control[]{name, target, category}) c.setPrefWidth(260);

        Label hint = new Label("Kemajuan dihitung dari semua transaksi di kategori yang dipilih.");
        hint.getStyleClass().add("caption");
        hint.setWrapText(true);
        hint.setMaxWidth(380);
        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(380);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Nama"), name);
        grid.addRow(1, new Label("Target (Rp)"), target);
        grid.addRow(2, new Label("Kategori"), category);
        grid.add(hint, 0, 3, 2, 1);
        grid.add(error, 0, 4, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.getStyleClass().add("primary");
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                String digits = target.getText() == null ? "" : target.getText().replaceAll("[^0-9]", "");
                if (digits.isEmpty()) throw new InvalidGoalException("Target wajib diisi");
                long amount = Long.parseLong(digits);
                java.util.UUID categoryId = category.getValue() == null ? null : category.getValue().getId();
                if (existing == null) game.addGoal(name.getText(), amount, categoryId);
                else game.updateGoal(existing.id(), name.getText(), amount, categoryId);
            } catch (InvalidGoalException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            } catch (NumberFormatException ex) {
                error.setText("Target terlalu besar");
                ev.consume();
            }
        });
        dialog.showAndWait();
    }
}
