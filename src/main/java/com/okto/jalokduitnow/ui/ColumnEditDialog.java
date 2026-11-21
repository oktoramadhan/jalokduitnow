package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.FieldKind;
import com.okto.jalokduitnow.schema.FieldType;
import com.okto.jalokduitnow.service.SchemaService;
import com.okto.jalokduitnow.service.SchemaService.TypeChangeImpact;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Add or edit a column. All rules live in SchemaService / TableSchema / FieldType; this dialog only collects input,
 * shows the service's messages inline, and asks for confirmation before anything that could lose data:
 * a type change shows how many values are converted and cleared, and removing a Choice option that is in use
 * asks which option those values should move to.
 */
public final class ColumnEditDialog {
    private ColumnEditDialog() {}

    /** @param existing the column to edit, or null to add a new one */
    public static void show(SchemaService service, ColumnDefinition existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Tambah Kolom" : "Ubah Kolom");
        ButtonType save = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, new ButtonType("Batal", ButtonBar.ButtonData.CANCEL_CLOSE));
        Theme.styleDialog(dialog.getDialogPane());

        TextField name = new TextField(existing == null ? "" : existing.getName());
        name.setPromptText("contoh: Jenis");
        ComboBox<FieldKind> kind = new ComboBox<>(FXCollections.observableArrayList(FieldKind.values()));
        kind.setConverter(new StringConverter<>() {
            @Override public String toString(FieldKind k) { return k == null ? "" : k.label(); }
            @Override public FieldKind fromString(String s) { return null; }
        });
        FieldKind originalKind = existing == null ? FieldKind.TEXT : FieldKind.of(existing.getType());
        kind.setValue(originalKind);
        CheckBox required = new CheckBox("Wajib diisi");
        required.setSelected(existing != null && existing.isRequired());

        OptionListEditor options = new OptionListEditor(existing != null && originalKind == FieldKind.CHOICE
                ? existing.getType().options() : List.of());
        VBox optionsBox = new VBox(8, new Label("Opsi"), options);
        optionsBox.managedProperty().bind(optionsBox.visibleProperty());

        Label typeNote = new Label("Mengubah tipe mengonversi nilai yang sudah ada. Kamu akan diminta konfirmasi sebelum diterapkan.");
        typeNote.getStyleClass().add("caption");
        typeNote.setWrapText(true);
        typeNote.setMaxWidth(380);
        typeNote.managedProperty().bind(typeNote.visibleProperty());

        Runnable refreshVisibility = () -> {
            optionsBox.setVisible(kind.getValue() != null && kind.getValue().needsOptions());
            typeNote.setVisible(existing != null && kind.getValue() != originalKind);
        };
        kind.setOnAction(e -> refreshVisibility.run());
        refreshVisibility.run();

        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(380);

        for (Control c : new Control[]{name, kind}) c.setPrefWidth(300);
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.addRow(0, new Label("Nama"), name);
        grid.addRow(1, new Label("Tipe"), kind);
        grid.add(required, 1, 2);
        VBox content = new VBox(14, grid, typeNote, optionsBox, error);
        content.setPadding(new Insets(16));
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);

        Button ok = (Button) dialog.getDialogPane().lookupButton(save);
        ok.getStyleClass().add("primary");
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                boolean saved = existing == null
                        ? add(service, name.getText(), kind.getValue(), options, required.isSelected())
                        : edit(service, existing, name.getText(), kind.getValue(), options, required.isSelected());
                if (!saved) ev.consume();            // the user declined a confirmation: stay in the dialog
            } catch (InvalidColumnException ex) {
                error.setText(ex.getMessage());      // duplicate name, empty name, Choice without options...
                ev.consume();
            }
        });
        dialog.showAndWait();
    }

    private static boolean add(SchemaService service, String name, FieldKind kind, OptionListEditor options,
                               boolean required) throws InvalidColumnException {
        FieldType<?> type = kind.create(options.options());
        service.addColumn(name, type, required);
        return true;
    }

    /** @return false if the user backed out at a confirmation (nothing has been changed then) */
    private static boolean edit(SchemaService service, ColumnDefinition column, String name, FieldKind kind,
                                OptionListEditor options, boolean required) throws InvalidColumnException {
        String id = column.getId();
        FieldKind oldKind = FieldKind.of(column.getType());

        // 1. validate everything that can be validated, before asking the user anything
        service.validateName(name, id);
        FieldType<?> newType = kind.create(options.options());

        boolean typeChanged = kind != oldKind;
        List<String> newOptions = options.options();
        boolean optionsChanged = !typeChanged && kind == FieldKind.CHOICE && !newOptions.equals(column.getType().options());

        // 2. confirmations: nothing has been applied yet, so "Batal" leaves the column exactly as it was
        Map<String, String> valueMapping = new HashMap<>();
        if (typeChanged) {
            TypeChangeImpact impact = service.previewTypeChange(id, newType);
            if (impact.total() > 0 && !confirmTypeChange(column, oldKind, kind, impact)) return false;
        } else if (optionsChanged) {
            valueMapping.putAll(options.renames());
            Map<String, Long> usage = service.optionUsage(id);
            Map<String, Long> needReplacement = new java.util.LinkedHashMap<>();
            for (String removed : options.removedOriginals()) {
                if (usage.getOrDefault(removed, 0L) > 0) needReplacement.put(removed, usage.get(removed));
            }
            if (!needReplacement.isEmpty()) {
                Optional<Map<String, String>> chosen = askReplacements(needReplacement, newOptions);
                if (chosen.isEmpty()) return false;
                valueMapping.putAll(chosen.get());
            }
        }

        // 3. apply
        service.updateColumn(id, name, required);
        if (typeChanged) service.changeType(id, newType, true);
        else if (optionsChanged) service.updateChoiceOptions(id, newOptions, valueMapping);
        return true;
    }

    private static boolean confirmTypeChange(ColumnDefinition column, FieldKind from, FieldKind to, TypeChangeImpact impact) {
        long converted = impact.total() - impact.incompatible();
        StringBuilder text = new StringBuilder("Kolom '" + column.getName() + "' berubah dari " + from.label()
                + " ke " + to.label() + ".\n\n");
        text.append(impact.total()).append(" transaksi punya nilai di kolom ini:\n");
        text.append("  - ").append(converted).append(" nilai akan dikonversi\n");
        text.append("  - ").append(impact.incompatible()).append(" nilai akan dikosongkan (tidak cocok dengan tipe baru)");
        if (impact.incompatible() > 0) text.append("\n\nNilai yang dikosongkan tidak bisa dikembalikan.");
        ButtonType change = new ButtonType("Ubah Tipe", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Batal", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.WARNING, text.toString(), change, cancel);
        alert.setHeaderText("Ubah tipe kolom");
        alert.setGraphic(warningIcon());
        Theme.styleDialog(alert.getDialogPane());
        ((Button) alert.getDialogPane().lookupButton(change)).getStyleClass().add("primary");
        return alert.showAndWait().filter(b -> b == change).isPresent();
    }

    static javafx.scene.Node warningIcon() {
        javafx.scene.Node icon = IconCatalog.icon("alert", 36);
        icon.getStyleClass().add("icon-warning");
        return icon;
    }

    /** One replacement choice per removed option that is still in use. Empty if cancelled. */
    private static Optional<Map<String, String>> askReplacements(Map<String, Long> removed, List<String> remaining) {
        if (remaining.isEmpty()) return Optional.empty();   // cannot happen (a Choice needs an option) but never leave a dead end
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Pindahkan nilai");
        dialog.setHeaderText("Opsi yang dihapus masih dipakai");
        ButtonType move = new ButtonType("Pindahkan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(move, new ButtonType("Batal", ButtonBar.ButtonData.CANCEL_CLOSE));
        Theme.styleDialog(dialog.getDialogPane());

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.setPadding(new Insets(12));
        Map<String, ComboBox<String>> boxes = new java.util.LinkedHashMap<>();
        int row = 0;
        for (Map.Entry<String, Long> e : removed.entrySet()) {
            ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(new ArrayList<>(remaining)));
            box.setValue(remaining.get(0));
            box.setPrefWidth(200);
            boxes.put(e.getKey(), box);
            grid.addRow(row++, new Label("'" + e.getKey() + "' dipakai di " + e.getValue() + " transaksi, pindahkan ke:"), box);
        }
        dialog.getDialogPane().setContent(grid);
        ((Button) dialog.getDialogPane().lookupButton(move)).getStyleClass().add("primary");
        dialog.setResultConverter(b -> {
            if (b != move) return null;
            Map<String, String> result = new HashMap<>();
            boxes.forEach((old, box) -> result.put(old, box.getValue()));
            return result;
        });
        return dialog.showAndWait();
    }
}
