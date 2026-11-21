package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.service.DeleteAction;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** The "Zona Berbahaya" card in Settings: one row per delete action. Each button opens the confirmation dialog. */
public class DangerZonePanel extends VBox {
    public DangerZonePanel(Consumer<DeleteAction> onAction) {
        setSpacing(16);
        getStyleClass().addAll("card", "danger-zone");
        setMaxWidth(620);

        Label title = new Label("Zona Berbahaya", IconCatalog.icon("alert", 20));
        title.getStyleClass().add("title-2");
        title.setGraphicTextGap(8);
        Label hint = new Label("Penghapusan data. Setiap tindakan meminta konfirmasi dan membuat cadangan pengaman otomatis lebih dulu.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        getChildren().addAll(title, hint,
                row(DeleteAction.TRANSACTIONS, "Hapus semua transaksi",
                        "Menghapus semua transaksi. Kolom kustom, kategori, donor, pengaturan, dan progres game tetap ada.", onAction),
                row(DeleteAction.GAME, "Reset progres game",
                        "Mengembalikan XP, level, streak, quest, badge, dan target tabungan ke awal. Transaksi dan pengaturan tetap ada.", onAction),
                row(DeleteAction.FACTORY, "Reset pabrik",
                        "Menghapus semuanya: transaksi, kolom kustom, kategori, donor, pengingat, pengaturan, dan progres game. "
                                + "Aplikasi kembali ke keadaan pertama kali dibuka.", onAction));
    }

    private static HBox row(DeleteAction action, String name, String description, Consumer<DeleteAction> onAction) {
        Label n = new Label(name);
        n.setStyle("-fx-font-weight: bold;");
        Label d = new Label(description);
        d.getStyleClass().add("muted");
        d.setWrapText(true);
        Button b = new Button("Hapus...", IconCatalog.icon("delete", 16));
        b.getStyleClass().add("danger");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> onAction.accept(action));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox text = new VBox(4, n, d);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(16, text, b);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
