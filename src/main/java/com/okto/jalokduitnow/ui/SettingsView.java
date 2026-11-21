package com.okto.jalokduitnow.ui;

import javafx.geometry.Pos;
import com.okto.jalokduitnow.service.BackupService;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.DonorSettings;
import com.okto.jalokduitnow.service.GameService;
import com.okto.jalokduitnow.service.RecipientService;
import java.time.Clock;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Settings page: the light/dark theme switch (saved in settings.properties). */
public class SettingsView extends VBox {
    public SettingsView(GameService game, RecipientService recipients, DonorSettings donorSettings,
                        CategoryService categories, BackupService backup, UiState state, Clock clock) {
        Label title = new Label("Tampilan");
        title.getStyleClass().add("title-2");

        Label name = new Label("Mode gelap");
        name.setStyle("-fx-font-weight: bold;");
        Label hint = new Label("Ganti tema terang dan gelap. Pilihanmu diingat saat aplikasi dibuka lagi.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        CheckBox dark = new CheckBox();
        dark.selectedProperty().bindBidirectional(Theme.darkProperty());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(16, new VBox(4, name, hint), spacer, dark);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(16, title, row);
        card.getStyleClass().add("card");
        card.setMaxWidth(620);

        Label gameTitle = new Label("Progres Game");
        gameTitle.getStyleClass().add("title-2");
        Label resetName = new Label("Reset progres game");
        resetName.setStyle("-fx-font-weight: bold;");
        Label resetHint = new Label("Mengembalikan XP, level, streak, badge, dan hadiah quest ke awal. "
                + "Transaksi, kategori, kolom, dan target tabunganmu tidak ikut terhapus.");
        resetHint.getStyleClass().add("muted");
        resetHint.setWrapText(true);
        Button reset = new Button("Reset", IconCatalog.icon("delete", 16));
        reset.getStyleClass().add("danger");
        reset.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Reset semua progres game? XP, level, streak, dan badge akan kembali ke awal. Tindakan ini tidak bisa dibatalkan.");
            confirm.setHeaderText("Reset progres game");
            Theme.styleDialog(confirm.getDialogPane());
            confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> game.reset());
        });
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        HBox resetRow = new HBox(16, new VBox(4, resetName, resetHint), spacer2, reset);
        resetRow.setAlignment(Pos.CENTER_LEFT);
        VBox gameCard = new VBox(16, gameTitle, resetRow);
        gameCard.getStyleClass().add("card");
        gameCard.setMaxWidth(620);

        setSpacing(20);
        getChildren().addAll(card, new DonorSettingsPanel(recipients, donorSettings, categories),
                new ExportPanel(backup, state, clock), gameCard);
    }
}
