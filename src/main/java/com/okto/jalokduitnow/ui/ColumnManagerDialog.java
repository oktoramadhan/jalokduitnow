package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.FieldKind;
import com.okto.jalokduitnow.service.SchemaService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Optional;

/**
 * The Columns page: the four fixed core columns (locked) and the user's custom columns, each with its type, a
 * required flag, a visibility toggle, up/down reordering, edit and delete. Everything is done through SchemaService;
 * the lists redraw when the schema changes, and the transaction table and form follow because they listen too.
 */
public final class ColumnManagerDialog {
    private record Core(String iconKey, String name, String type) {}

    private static final List<Core> CORE_COLUMNS = List.of(
            new Core("calendar", "Tanggal", "Tanggal"),
            new Core("transactions", "Tipe", "Income / Expense"),
            new Core("categories", "Kategori", "Pilihan"),
            new Core("wallet", "Jumlah", "Angka (Rupiah)"));

    private final SchemaService service;
    private final VBox customList = new VBox(12);

    private ColumnManagerDialog(SchemaService service) {
        this.service = service;
    }

    /** The manager as a node, for the Kolom page. */
    public static Node createView(SchemaService service) {
        return new ColumnManagerDialog(service).buildView();
    }

    /** The same manager in a window. */
    public static void show(SchemaService service) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Kelola Kolom");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Theme.styleDialog(dialog.getDialogPane());
        dialog.getDialogPane().setContent(createView(service));
        dialog.showAndWait();
    }

    private Node buildView() {
        VBox coreList = new VBox(10);
        for (Core core : CORE_COLUMNS) coreList.getChildren().add(coreRow(core));

        Button add = new Button("Tambah Kolom", IconCatalog.icon("plus", 16));
        add.getStyleClass().add("primary");
        add.setOnAction(e -> ColumnEditDialog.show(service, null));

        VBox root = new VBox(24,
                section("Kolom Inti", "Selalu ada dan tidak bisa diubah", null, coreList),
                section("Kolom Kustom", "Muncul di tabel dan form transaksi", add, customList));
        service.addListener(this::render);
        render();
        return root;
    }

    // ---- drawing ----

    private void render() {
        customList.getChildren().clear();
        List<ColumnDefinition> columns = service.getSchema().getColumns();
        if (columns.isEmpty()) {
            Label none = new Label("Belum ada kolom kustom. Tambah satu untuk mencatat hal lain di setiap transaksi.");
            none.getStyleClass().add("muted");
            customList.getChildren().add(none);
            return;
        }
        for (int i = 0; i < columns.size(); i++) customList.getChildren().add(customRow(columns.get(i), i, columns.size()));
    }

    private Node coreRow(Core core) {
        Label name = new Label(core.name());
        name.setStyle("-fx-font-weight: bold;");
        Label lock = new Label("Terkunci", IconCatalog.icon("lock", 14));
        lock.getStyleClass().add("muted");
        lock.setGraphicTextGap(6);
        HBox row = new HBox(14, Tiles.tile(core.iconKey(), "#9CA3AF", 40), name, chip(core.type(), null), spacer(), lock);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("card", "compact");
        return row;
    }

    private Node customRow(ColumnDefinition col, int index, int count) {
        FieldKind kind = FieldKind.of(col.getType());
        Label name = new Label(col.getName());
        name.setStyle("-fx-font-weight: bold;");

        String typeText = kind.label() + (kind == FieldKind.CHOICE ? " (" + col.getType().options().size() + ")" : "");
        HBox chips = new HBox(8, chip(typeText, kind.iconKey()));
        if (col.isRequired()) chips.getChildren().add(chip("Wajib", null, "reward"));
        if (!col.isVisible()) chips.getChildren().add(chip("Tersembunyi", null));
        chips.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(6, name, chips);
        if (kind == FieldKind.CHOICE) {
            Label opts = new Label(String.join("  /  ", col.getType().options()));
            opts.getStyleClass().add("caption");
            opts.setWrapText(true);
            opts.setMaxWidth(420);
            text.getChildren().add(opts);
        }

        Button up = iconButton("up", "Naikkan", index == 0, () -> run(() -> service.move(col.getId(), index - 1)));
        Button down = iconButton("down", "Turunkan", index == count - 1, () -> run(() -> service.move(col.getId(), index + 1)));
        Button eye = iconButton(col.isVisible() ? "eye" : "eye-off",
                col.isVisible() ? "Sembunyikan dari tabel dan form" : "Tampilkan lagi", false,
                () -> run(() -> service.setVisible(col.getId(), !col.isVisible())));
        Button edit = iconButton("edit", "Ubah kolom", false, () -> ColumnEditDialog.show(service, col));
        Button delete = iconButton("delete", "Hapus kolom", false, () -> confirmDelete(col));
        delete.getStyleClass().add("danger-text");

        HBox row = new HBox(14, Tiles.tile(kind.iconKey(), col.isVisible() ? "#3B82F6" : "#9CA3AF", 40), text, spacer(),
                up, down, eye, edit, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("card", "compact");
        if (!col.isVisible()) row.setOpacity(0.7);
        return row;
    }

    // ---- delete: never silent ----

    private void confirmDelete(ColumnDefinition col) {
        try {
            long used = service.previewDelete(col.getId());
            if (used == 0) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Hapus kolom '" + col.getName() + "'? Belum ada transaksi yang memakainya.");
                Theme.styleDialog(confirm.getDialogPane());
                if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) service.deleteColumn(col.getId(), false);
                return;
            }
            ButtonType hide = new ButtonType("Sembunyikan saja", ButtonBar.ButtonData.OK_DONE);
            ButtonType delete = new ButtonType("Hapus tetap", ButtonBar.ButtonData.OTHER);
            ButtonType cancel = new ButtonType("Batal", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert ask = new Alert(Alert.AlertType.WARNING,
                    used + " transaksi punya nilai di kolom '" + col.getName() + "'.\n\n"
                            + "Menghapus kolom ini menghapus semua nilai itu dan tidak bisa dibatalkan.\n"
                            + "Menyembunyikan kolom menyimpan semua nilainya, dan kolom bisa ditampilkan lagi kapan saja.",
                    hide, delete, cancel);
            ask.setHeaderText("Hapus kolom '" + col.getName() + "'?");
            ask.setGraphic(ColumnEditDialog.warningIcon());
            Theme.styleDialog(ask.getDialogPane());
            ((Button) ask.getDialogPane().lookupButton(hide)).getStyleClass().add("primary"); // the safe choice stands out
            ((Button) ask.getDialogPane().lookupButton(delete)).getStyleClass().add("danger");
            Optional<ButtonType> choice = ask.showAndWait();
            if (choice.isEmpty() || choice.get() == cancel) return;
            if (choice.get() == hide) service.setVisible(col.getId(), false);
            else service.deleteColumn(col.getId(), true);   // the user has just seen how many values go
        } catch (InvalidColumnException e) {
            showError(e.getMessage());
        }
    }

    // ---- small helpers ----

    @FunctionalInterface
    private interface SchemaAction { void run() throws InvalidColumnException; }

    private void run(SchemaAction action) {
        try {
            action.run();
        } catch (InvalidColumnException e) {
            showError(e.getMessage());
        }
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        Theme.styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private static Button iconButton(String iconKey, String tip, boolean disabled, Runnable action) {
        Button b = new Button();
        b.setGraphic(IconCatalog.icon(iconKey, 18));
        b.getStyleClass().add("ghost");
        b.setTooltip(new Tooltip(tip));
        b.setDisable(disabled);
        b.setOnAction(e -> action.run());
        return b;
    }

    private static HBox chip(String text, String iconKey, String... extraClasses) {
        HBox chip = new HBox(6);
        if (iconKey != null) chip.getChildren().add(IconCatalog.icon(iconKey, 13));
        chip.getChildren().add(new Label(text));
        chip.setAlignment(Pos.CENTER);
        chip.getStyleClass().add("chip");
        chip.getStyleClass().addAll(extraClasses);
        chip.setMaxWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static VBox section(String title, String subtitle, Node action, Node content) {
        Label t = new Label(title);
        t.getStyleClass().add("title-2");
        Label s = new Label(subtitle);
        s.getStyleClass().add("muted");
        HBox header = new HBox(12, t, s, spacer());
        if (action != null) header.getChildren().add(action);
        header.setAlignment(Pos.CENTER_LEFT);
        return new VBox(14, header, content);
    }
}
