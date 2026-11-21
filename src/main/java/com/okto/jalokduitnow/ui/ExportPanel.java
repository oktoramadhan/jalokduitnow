package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.service.BackupService;
import com.okto.jalokduitnow.storage.CsvExporter.Delimiter;
import com.okto.jalokduitnow.thread.ExportTask;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;

/**
 * Settings card for exporting data: transactions as CSV (for spreadsheets) and a full backup as JSON.
 * The text is built on the FX thread (a snapshot of the data), then a background Task writes the file;
 * the result comes back on the FX thread.
 */
public class ExportPanel extends VBox {
    private final BackupService backup;
    private final UiState state;
    private final Clock clock;

    private final RadioButton allMonths = new RadioButton("Semua transaksi");
    private final RadioButton oneMonth = new RadioButton();
    private final ComboBox<Delimiter> delimiter = new ComboBox<>(FXCollections.observableArrayList(Delimiter.values()));
    private final CheckBox includeDonors = new CheckBox("Sertakan daftar donor dan nomor telepon");
    private final Button csvButton = new Button("Ekspor CSV...", IconCatalog.icon("report", 16));
    private final Button jsonButton = new Button("Buat Cadangan...", IconCatalog.icon("copy", 16));
    private final Label status = new Label();
    private File lastFolder;

    public ExportPanel(BackupService backup, UiState state, Clock clock) {
        this.backup = backup;
        this.state = state;
        this.clock = clock;

        setSpacing(16);
        getStyleClass().add("card");
        setMaxWidth(620);

        Label title = new Label("Ekspor dan Cadangan");
        title.getStyleClass().add("title-2");

        // ---- CSV ----
        Label csvName = new Label("Ekspor transaksi (CSV)");
        csvName.setStyle("-fx-font-weight: bold;");
        Label csvHint = new Label("Untuk dibuka di Excel atau Google Sheets. Berisi kolom inti dan semua kolom kustom.");
        csvHint.getStyleClass().add("muted");
        csvHint.setWrapText(true);

        ToggleGroup scope = new ToggleGroup();
        allMonths.setToggleGroup(scope);
        oneMonth.setToggleGroup(scope);
        allMonths.setSelected(true);
        oneMonth.textProperty().bind(Bindings.createStringBinding(() -> {
            YearMonth m = state.period.get();
            return "Hanya bulan terpilih: " + m.getMonth().getDisplayName(TextStyle.FULL, Formats.ID) + " " + m.getYear();
        }, state.period));

        delimiter.setValue(Delimiter.COMMA);
        delimiter.setConverter(new StringConverter<>() {
            @Override public String toString(Delimiter d) { return d == null ? "" : d.label(); }
            @Override public Delimiter fromString(String s) { return null; }
        });
        Label delimiterHint = new Label("Excel dengan pengaturan regional Indonesia biasanya memakai titik koma.");
        delimiterHint.getStyleClass().add("caption");
        delimiterHint.setWrapText(true);
        HBox delimiterRow = new HBox(12, new Label("Pemisah"), delimiter);
        delimiterRow.setAlignment(Pos.CENTER_LEFT);

        csvButton.getStyleClass().add("primary");
        csvButton.setOnAction(e -> exportCsv());

        // ---- JSON ----
        Label jsonName = new Label("Cadangan lengkap (JSON)");
        jsonName.setStyle("-fx-font-weight: bold;");
        Label jsonHint = new Label("Satu file berisi semua data: transaksi, kolom, kategori, progres game, dan donor. "
                + "Simpan di tempat yang aman.");
        jsonHint.getStyleClass().add("muted");
        jsonHint.setWrapText(true);
        includeDonors.setSelected(true);
        jsonButton.setOnAction(e -> exportJson());

        status.getStyleClass().add("caption");
        status.setWrapText(true);

        Region line = new Region();
        line.getStyleClass().add("divider");
        getChildren().addAll(title,
                new VBox(8, csvName, csvHint, allMonths, oneMonth, delimiterRow, delimiterHint, csvButton),
                line,
                new VBox(8, jsonName, jsonHint, includeDonors, jsonButton),
                status);
    }

    // ---- actions ----

    private void exportCsv() {
        YearMonth month = oneMonth.isSelected() ? state.period.get() : null;
        String text = backup.transactionsCsv(month, delimiter.getValue());          // snapshot, on the FX thread
        int count = backup.transactionsFor(month).size();
        String scopeName = month == null ? "semua" : month.toString();
        File file = choose("Ekspor transaksi", "jalokduitnow-transaksi-" + scopeName + "-" + today() + ".csv",
                new FileChooser.ExtensionFilter("CSV", "*.csv"), ".csv");
        if (file == null) return;
        write(file.toPath(), text, true, count + " transaksi diekspor ke ");   // BOM: Excel on Windows needs it for accents
    }

    private void exportJson() {
        String text;
        try {
            text = backup.backupJson(includeDonors.isSelected());
        } catch (StorageException e) {
            showError(e.getMessage());
            return;
        }
        File file = choose("Simpan cadangan", "jalokduitnow-cadangan-" + today() + ".json",
                new FileChooser.ExtensionFilter("JSON", "*.json"), ".json");
        if (file == null) return;
        write(file.toPath(), text, false, "Cadangan disimpan di ");
    }

    private void write(Path target, String text, boolean bom, String successPrefix) {
        ExportTask task = new ExportTask(target, text, bom);
        setBusy(true);
        status.setText("Menulis " + target.getFileName() + "...");
        task.setOnSucceeded(e -> {                                                  // runs on the FX thread
            setBusy(false);
            status.setText(successPrefix + task.getValue());
        });
        task.setOnFailed(e -> {
            setBusy(false);
            status.setText("");
            Throwable error = task.getException();
            showError(error == null ? "Ekspor gagal" : error.getMessage());
        });
        Thread t = new Thread(task, "export");
        t.setDaemon(true);
        t.start();
    }

    private File choose(String title, String fileName, FileChooser.ExtensionFilter filter, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(fileName);
        chooser.getExtensionFilters().add(filter);
        File start = lastFolder != null ? lastFolder : new File(System.getProperty("user.home"));
        if (start.isDirectory()) chooser.setInitialDirectory(start);
        Node anchor = getScene() == null ? null : getScene().getRoot();
        File file = chooser.showSaveDialog(anchor == null ? null : anchor.getScene().getWindow());
        if (file == null) return null;
        lastFolder = file.getParentFile();
        // some platforms do not add the extension themselves
        return file.getName().toLowerCase().endsWith(extension) ? file : new File(file.getPath() + extension);
    }

    private void setBusy(boolean busy) {
        csvButton.setDisable(busy);
        jsonButton.setDisable(busy);
    }

    private String today() {
        return LocalDate.now(clock).toString();
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        Theme.styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }
}
