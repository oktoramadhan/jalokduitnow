package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.service.AutoBackupService;
import com.okto.jalokduitnow.service.BackupService;
import com.okto.jalokduitnow.service.RestoreService;
import com.okto.jalokduitnow.service.RestoreService.RestoreResult;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.Clock;
import java.util.function.Consumer;

/** The "Pulihkan dari Cadangan" card in Settings: a short explanation and the button that opens the restore dialog. */
public class RestorePanel extends VBox {
    public RestorePanel(RestoreService service, BackupService backup, AutoBackupService autoBackups, Runnable flushAll,
                        Clock clock, Consumer<RestoreResult> onRestored) {
        setSpacing(12);
        getStyleClass().add("card");
        setMaxWidth(620);

        Label title = new Label("Pulihkan dari Cadangan");
        title.getStyleClass().add("title-2");
        Label hint = new Label("Kembalikan semua data dari file cadangan atau dari cadangan otomatis. File diperiksa dulu, data saat ini "
                + "disimpan sebagai cadangan pengaman sebelum diganti, dan jika sesuatu gagal data lama dikembalikan.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        Button open = new Button("Pulihkan dari cadangan...", IconCatalog.icon("restore", 16));
        open.setOnAction(e -> RestoreDialog.show(service, backup, autoBackups, flushAll, clock, onRestored));
        getChildren().addAll(title, hint, open);
    }
}
