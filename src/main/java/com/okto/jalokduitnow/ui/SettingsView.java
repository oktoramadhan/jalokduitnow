package com.okto.jalokduitnow.ui;

import javafx.geometry.Pos;
import com.okto.jalokduitnow.service.BackupService;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.DonorSettings;
import com.okto.jalokduitnow.service.GameService;
import com.okto.jalokduitnow.service.RecipientService;
import com.okto.jalokduitnow.service.ReminderSettingsService;
import com.okto.jalokduitnow.thread.ReminderService;
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
                        CategoryService categories, BackupService backup, UiState state, Clock clock,
                        ReminderSettingsService reminderSettings, ReminderService reminders, boolean traySupported,
                        javafx.scene.Node restoreCard, javafx.scene.Node dangerZone) {
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

        setSpacing(20);
        getChildren().addAll(card, new DonorSettingsPanel(recipients, donorSettings, categories),
                new RemindersPanel(reminderSettings, reminders, traySupported),
                new ExportPanel(backup, state, clock), restoreCard, dangerZone);
    }
}
