package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.TransactionDto;
import com.okto.jalokduitnow.thread.ImportTask;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/** Entry point. Stage 3: tabs for Dashboard and Transaksi, plus background Excel import. */
public class MainApp extends Application {
    private FinanceService service;
    private final Label status = new Label();
    private final ProgressBar progress = new ProgressBar(0);

    @Override
    public void start(Stage stage) {
        service = new FinanceService(new JsonRepository<>(
                JsonRepository.defaultDataDir().resolve("transactions.json"), TransactionDto.class));
        try {
            service.load();
        } catch (StorageException e) {
            showError(e.getMessage());
        }

        TabPane tabs = new TabPane(
                new Tab("Dashboard", new DashboardView(service)),
                new Tab("Transaksi", new TransactionView(service)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Button importBtn = new Button("Import Excel...");
        importBtn.setOnAction(ev -> chooseAndImport(stage, importBtn));
        progress.setPrefWidth(180);
        HBox top = new HBox(10, importBtn, progress, status);
        top.setPadding(new Insets(10));
        HBox.setHgrow(status, Priority.ALWAYS);

        BorderPane root = new BorderPane(tabs);
        root.setTop(top);

        stage.setScene(new Scene(root, 1000, 650));
        stage.setTitle("JalokDuitNow");
        stage.show();
    }

    private void chooseAndImport(Stage stage, Button importBtn) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        ImportTask task = new ImportTask(file.toPath());
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        importBtn.setDisable(true);

        // Task callbacks already run on the FX thread.
        task.setOnSucceeded(e -> {
            finish(importBtn);
            try {
                service.replaceAll(task.getValue().transactions());
                int warn = task.getValue().warnings().size();
                status.setText("Import selesai: " + task.getValue().transactions().size()
                        + " transaksi" + (warn > 0 ? ", " + warn + " baris dilewati" : ""));
            } catch (StorageException ex) {
                showError(ex.getMessage());
            }
        });
        task.setOnFailed(e -> {
            finish(importBtn);
            showError(task.getException().getMessage());
        });

        Thread t = new Thread(task, "excel-import");
        t.setDaemon(true);
        t.start();
    }

    private void finish(Button importBtn) {
        progress.progressProperty().unbind();
        status.textProperty().unbind();
        importBtn.setDisable(false);
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
