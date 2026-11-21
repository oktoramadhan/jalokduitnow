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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;

/** Entry point. Stage 2: import Excel in the background and show totals. */
public class MainApp extends Application {
    private static final NumberFormat RUPIAH = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));

    private FinanceService service;
    private final Label summary = new Label();
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

        Button importBtn = new Button("Import Excel...");
        importBtn.setOnAction(ev -> chooseAndImport(stage, importBtn));
        progress.setMaxWidth(Double.MAX_VALUE);

        VBox root = new VBox(12, new Label("JalokDuitNow"), summary, importBtn, progress, status);
        root.setPadding(new Insets(20));
        refreshSummary();

        stage.setScene(new Scene(root, 800, 500));
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
            progress.progressProperty().unbind();
            status.textProperty().unbind();
            importBtn.setDisable(false);
            try {
                service.replaceAll(task.getValue().transactions());
                int warn = task.getValue().warnings().size();
                status.setText("Import selesai: " + task.getValue().transactions().size()
                        + " transaksi" + (warn > 0 ? ", " + warn + " baris dilewati" : ""));
                refreshSummary();
            } catch (StorageException ex) {
                showError(ex.getMessage());
            }
        });
        task.setOnFailed(e -> {
            progress.progressProperty().unbind();
            status.textProperty().unbind();
            importBtn.setDisable(false);
            showError(task.getException().getMessage());
        });

        Thread t = new Thread(task, "excel-import");
        t.setDaemon(true);
        t.start();
    }

    private void refreshSummary() {
        summary.setText("Income: " + RUPIAH.format(service.totalIncome())
                + "   Expense: " + RUPIAH.format(service.totalExpense())
                + "   Balance: " + RUPIAH.format(service.balance())
                + "   (" + service.getAll().size() + " transaksi)");
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
