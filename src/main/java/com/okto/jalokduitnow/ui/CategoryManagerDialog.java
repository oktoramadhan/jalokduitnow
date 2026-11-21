package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.service.CategoryService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Category manager: a list with color/icon preview, an add/edit form, archive, merge and delete.
 * Deleting or merging a category that has transactions always asks for a target category first.
 */
public final class CategoryManagerDialog {
    private final CategoryService service;
    private final ObservableList<Category> items = FXCollections.observableArrayList();
    private final ListView<Category> list = new ListView<>(items);
    private final TextField name = new TextField();
    private final ComboBox<CategoryType> type = new ComboBox<>(FXCollections.observableArrayList(CategoryType.values()));
    private final FlowPane iconPicker = new FlowPane(6, 6);
    private final ToggleGroup iconGroup = new ToggleGroup();
    private final ColorPicker color = new ColorPicker(Color.web("#1f9d6b"));
    private final TextField budget = new TextField();
    private final Label error = new Label();
    private final Button save = new Button();
    private final Button archive = new Button("Arsipkan");
    private final Button merge = new Button("Gabung ke...");
    private final Button delete = new Button("Hapus...");

    private CategoryManagerDialog(CategoryService service) {
        this.service = service;
    }

    public static void show(CategoryService service) {
        new CategoryManagerDialog(service).open();
    }

    /** The manager as a plain node, for embedding in the Kategori page. */
    public static Node createView(CategoryService service) {
        return new CategoryManagerDialog(service).buildView();
    }

    private void open() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Kelola Kategori");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Theme.styleDialog(dialog.getDialogPane());
        dialog.getDialogPane().setContent(buildView());
        dialog.showAndWait();
    }

    private Node buildView() {
        list.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label label = new Label(c.getName());
                label.setStyle("-fx-font-weight: bold;");
                Label tag = new Label(c.getType().label()
                        + (c.getMonthlyBudget() != null ? " · budget " + Formats.rupiah(c.getMonthlyBudget()) : "")
                        + (c.isArchived() ? " · diarsipkan" : ""));
                tag.getStyleClass().add("muted");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(12, Tiles.tile(c, 36), label, spacer, tag);
                row.setAlignment(Pos.CENTER_LEFT);
                if (c.isArchived()) row.setOpacity(0.55);
                setGraphic(row);
            }
        });
        list.setPrefWidth(420);
        list.setPrefHeight(440);
        list.getSelectionModel().selectedItemProperty().addListener((o, a, selected) -> fillForm(selected));

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(14);
        name.setPromptText("Nama kategori");
        buildIconPicker();
        budget.setPromptText("kosong = tanpa budget");
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(CategoryType t) { return t == null ? "" : t.label(); }
            @Override public CategoryType fromString(String s) { return null; }
        });
        for (javafx.scene.Node field : new javafx.scene.Node[]{name, budget}) ((Control) field).setPrefWidth(240);
        type.setPrefWidth(240);
        color.setPrefWidth(240);
        type.setValue(CategoryType.EXPENSE);
        type.setOnAction(e -> budget.setDisable(type.getValue() == CategoryType.INCOME));
        form.addRow(0, new Label("Nama"), name);
        form.addRow(1, new Label("Tipe"), type);
        form.addRow(2, new Label("Ikon"), iconPicker);
        form.addRow(3, new Label("Warna"), color);
        form.addRow(4, new Label("Budget/bulan"), budget);

        Button fresh = new Button("Baru");
        save.getStyleClass().add("primary");
        delete.getStyleClass().add("danger");
        fresh.setOnAction(e -> list.getSelectionModel().clearSelection());
        save.setOnAction(e -> onSave());
        archive.setOnAction(e -> onArchive());
        merge.setOnAction(e -> onMerge());
        delete.setOnAction(e -> onDelete());
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(300);

        VBox right = new VBox(16, form, new HBox(8, fresh, save), new HBox(8, archive, merge, delete), error);
        HBox content = new HBox(24, list, right);
        content.setPadding(new Insets(4));
        service.addListener(this::reload); // (append-only listener list; short-lived dialogs leave a harmless stale refresh)
        reload();
        fillForm(null);
        return content;
    }

    private void reload() {
        Category selected = list.getSelectionModel().getSelectedItem();
        UUID keep = selected == null ? null : selected.getId();
        List<Category> sorted = service.getAll().stream()
                .sorted(Comparator.comparing(Category::getType).reversed() // Expense before Income
                        .thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        items.setAll(sorted);
        if (keep != null) service.find(keep).ifPresent(c -> list.getSelectionModel().select(c));
    }

    /** Selection -> edit form; no selection -> empty "add" form. */
    private void fillForm(Category c) {
        error.setText("");
        boolean editing = c != null;
        name.setText(editing ? c.getName() : "");
        selectIcon(editing ? c.getIconKey() : Category.DEFAULT_ICON);
        color.setValue(Color.web(editing ? c.getColor() : "#1f9d6b"));
        budget.setText(editing && c.getMonthlyBudget() != null ? String.valueOf(c.getMonthlyBudget()) : "");
        if (editing) type.setValue(c.getType());
        type.setDisable(editing); // the type never changes: transactions depend on it
        budget.setDisable(type.getValue() == CategoryType.INCOME);
        save.setText(editing ? "Simpan" : "Tambah");
        archive.setText(editing && c.isArchived() ? "Aktifkan" : "Arsipkan");
        archive.setDisable(!editing);
        merge.setDisable(!editing);
        delete.setDisable(!editing);
    }

    /** The picker: one toggle per icon in IconCatalog; the selected one is highlighted. */
    private void buildIconPicker() {
        iconPicker.setPrefWrapLength(240);
        for (String key : IconCatalog.categoryKeys()) {
            ToggleButton b = new ToggleButton();
            b.setGraphic(IconCatalog.icon(key, 18));
            b.setUserData(key);
            b.setToggleGroup(iconGroup);
            b.getStyleClass().add("icon-choice");
            b.setTooltip(new Tooltip(key));
            iconPicker.getChildren().add(b);
        }
    }

    private void selectIcon(String key) {
        for (Toggle t : iconGroup.getToggles()) t.setSelected(t.getUserData().equals(IconCatalog.normalize(key)));
    }

    private String selectedIcon() {
        Toggle t = iconGroup.getSelectedToggle();
        return t == null ? Category.DEFAULT_ICON : (String) t.getUserData();
    }

    private Long parseBudget() throws InvalidCategoryException {
        String digits = budget.getText() == null ? "" : budget.getText().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new InvalidCategoryException("Budget terlalu besar");
        }
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    private void onSave() {
        try {
            Category selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Category added = service.add(name.getText(), type.getValue(), selectedIcon(), hex(color.getValue()), parseBudget());
                list.getSelectionModel().select(added);
            } else {
                service.update(selected.getId(), name.getText(), selectedIcon(), hex(color.getValue()), parseBudget());
            }
            error.setText("");
        } catch (InvalidCategoryException ex) {
            error.setText(ex.getMessage());
        }
    }

    private void onArchive() {
        Category selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            service.archive(selected.getId(), !selected.isArchived());
            fillForm(selected);
        } catch (InvalidCategoryException ex) {
            error.setText(ex.getMessage());
        }
    }

    private void onMerge() {
        Category source = list.getSelectionModel().getSelectedItem();
        if (source == null) return;
        try {
            long used = service.usageCount(source.getId());
            pickTarget(source, "Gabungkan '" + source.getName() + "'",
                    used + " transaksi akan dipindahkan ke kategori tujuan, lalu '" + source.getName() + "' dihapus.")
                    .ifPresent(target -> {
                        try {
                            service.merge(source.getId(), target.getId());
                        } catch (InvalidCategoryException ex) {
                            error.setText(ex.getMessage());
                        }
                    });
        } catch (InvalidCategoryException ex) {
            error.setText(ex.getMessage());
        }
    }

    private void onDelete() {
        Category source = list.getSelectionModel().getSelectedItem();
        if (source == null) return;
        try {
            long used = service.usageCount(source.getId());
            if (used == 0) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Hapus kategori '" + source.getName() + "'?");
                Theme.styleDialog(confirm.getDialogPane());
                if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) service.delete(source.getId(), null);
                return;
            }
            // transactions use it: offer reassign or archive, never a silent delete
            ButtonType reassign = new ButtonType("Pindahkan lalu hapus", ButtonBar.ButtonData.OK_DONE);
            ButtonType archiveOnly = new ButtonType("Arsipkan saja", ButtonBar.ButtonData.OTHER);
            Alert ask = new Alert(Alert.AlertType.WARNING,
                    used + " transaksi memakai kategori '" + source.getName() + "'.\n"
                            + "Pindahkan ke kategori lain sebelum menghapus, atau arsipkan saja (transaksi tetap utuh).",
                    reassign, archiveOnly, ButtonType.CANCEL);
            ask.setHeaderText("Kategori masih dipakai");
            Theme.styleDialog(ask.getDialogPane());
            Optional<ButtonType> choice = ask.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return;
            if (choice.get() == archiveOnly) {
                service.archive(source.getId(), true);
                return;
            }
            pickTarget(source, "Pindahkan transaksi", used + " transaksi akan dipindahkan ke:")
                    .ifPresent(target -> {
                        try {
                            service.delete(source.getId(), target.getId());
                        } catch (InvalidCategoryException ex) {
                            error.setText(ex.getMessage());
                        }
                    });
        } catch (InvalidCategoryException ex) {
            error.setText(ex.getMessage());
        }
    }

    /** Asks for a different, active category of the same type. Empty if cancelled or none exists. */
    private Optional<Category> pickTarget(Category source, String title, String message) {
        List<Category> candidates = service.active(source.getType()).stream()
                .filter(c -> !c.getId().equals(source.getId())).toList();
        if (candidates.isEmpty()) {
            error.setText("Tidak ada kategori " + source.getType().label() + " lain sebagai tujuan. Tambah dulu satu.");
            return Optional.empty();
        }
        ChoiceDialog<Category> choose = new ChoiceDialog<>(candidates.get(0), candidates);
        choose.setTitle(title);
        choose.setHeaderText(message);
        choose.setContentText("Kategori tujuan:");
        Theme.styleDialog(choose.getDialogPane());
        return choose.showAndWait();
    }
}
