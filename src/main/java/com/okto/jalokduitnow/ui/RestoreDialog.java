package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.service.AutoBackupService;
import com.okto.jalokduitnow.service.AutoBackupService.BackupFile;
import com.okto.jalokduitnow.service.BackupPreview;
import com.okto.jalokduitnow.service.BackupService;
import com.okto.jalokduitnow.service.RestoreService;
import com.okto.jalokduitnow.service.RestoreService.RestorePlan;
import com.okto.jalokduitnow.service.RestoreService.RestoreResult;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * The restore flow, one step at a time:
 * <ol>
 *   <li>Pick a backup file, or one of the automatic backups from the list.</li>
 *   <li>The file is read and validated in a background Task (with a progress indicator). Nothing is changed.</li>
 *   <li>A preview compares the backup with the current data.</li>
 *   <li>Replacing needs an explicit confirmation: the user must type RESTORE. A safety backup of the current data is made
 *       automatically first, and its file name is shown in the result.</li>
 *   <li>The replacement runs in a background Task with a progress bar; every result comes back on the FX thread.</li>
 * </ol>
 * The rules (validation, atomic swap, rollback, reload) are all in RestoreService; this class only drives them and shows messages.
 */
public final class RestoreDialog {
    /** What the user must type to confirm replacing the current data. */
    static final String CONFIRM_WORD = "RESTORE";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Formats.ID);

    private final RestoreService service;
    private final BackupService backup;
    private final AutoBackupService autoBackups;
    private final Runnable flushAll;
    private final Clock clock;
    private final Consumer<RestoreResult> onRestored;

    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox sourceBox = new VBox(12);
    private final ListView<BackupFile> autoList = new ListView<>();
    private final HBox busyBox = new HBox(12);
    private final Label busyText = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final Label error = new Label();
    private final VBox previewBox = new VBox(14);
    private final VBox doneBox = new VBox(12);
    private File lastFolder;
    private RestorePlan plan;

    private RestoreDialog(RestoreService service, BackupService backup, AutoBackupService autoBackups, Runnable flushAll,
                          Clock clock, Consumer<RestoreResult> onRestored) {
        this.service = service;
        this.backup = backup;
        this.autoBackups = autoBackups;
        this.flushAll = flushAll;
        this.clock = clock;
        this.onRestored = onRestored;
    }

    public static void show(RestoreService service, BackupService backup, AutoBackupService autoBackups, Runnable flushAll,
                            Clock clock, Consumer<RestoreResult> onRestored) {
        new RestoreDialog(service, backup, autoBackups, flushAll, clock, onRestored).open(null);
    }

    /** Opens straight at the preview of one backup (the "Restore it" shortcut after a deletion). */
    public static void show(RestoreService service, BackupService backup, AutoBackupService autoBackups, Runnable flushAll,
                            Clock clock, Consumer<RestoreResult> onRestored, Path preselected) {
        new RestoreDialog(service, backup, autoBackups, flushAll, clock, onRestored).open(preselected);
    }

    // =========================================================================
    // layout
    // =========================================================================

    private void open(Path preselected) {
        dialog.setTitle("Pulihkan dari Cadangan");
        ButtonType close = new ButtonType("Tutup", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);
        Theme.styleDialog(dialog.getDialogPane());

        Label title = new Label("Pulihkan dari Cadangan");
        title.getStyleClass().add("title-2");
        Label hint = new Label("Pilih file cadangan (JSON) yang dibuat aplikasi ini. File diperiksa lebih dulu dan tidak ada data yang berubah sampai kamu mengonfirmasi.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        Button choose = new Button("Pilih file cadangan...", IconCatalog.icon("restore", 16));
        choose.getStyleClass().add("primary");
        choose.setOnAction(e -> chooseFile());

        Label autoTitle = new Label("Atau pulihkan dari cadangan otomatis");
        autoTitle.setStyle("-fx-font-weight: bold;");
        autoList.setPrefHeight(150);
        autoList.setPlaceholder(new Label("Belum ada cadangan otomatis. Satu dibuat setiap hari saat aplikasi dibuka."));
        autoList.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(BackupFile f, boolean empty) {
                super.updateItem(f, empty);
                setText(empty || f == null ? null : describe(f));
            }
        });
        autoList.getItems().setAll(autoBackups.list());
        Button useAuto = new Button("Periksa cadangan terpilih");
        useAuto.disableProperty().bind(autoList.getSelectionModel().selectedItemProperty().isNull());
        useAuto.setOnAction(e -> prepare(autoList.getSelectionModel().getSelectedItem().path()));

        sourceBox.getChildren().addAll(hint, choose, autoTitle, autoList, useAuto);

        progress.setPrefWidth(220);
        busyBox.setAlignment(Pos.CENTER_LEFT);
        busyBox.getChildren().addAll(progress, busyText);
        hide(busyBox);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(520);
        hide(error);
        hide(previewBox);
        hide(doneBox);

        VBox content = new VBox(16, title, sourceBox, busyBox, error, previewBox, doneBox);
        content.setPadding(new Insets(8));
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);
        if (preselected != null) javafx.application.Platform.runLater(() -> prepare(preselected));
        dialog.showAndWait();
    }

    // =========================================================================
    // step 1 and 2: choose and validate (background)
    // =========================================================================

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih file cadangan");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Cadangan JalokDuitNow (JSON)", "*.json"));
        File start = lastFolder != null ? lastFolder : new File(System.getProperty("user.home"));
        if (start.isDirectory()) chooser.setInitialDirectory(start);
        File file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
        if (file == null) return;
        lastFolder = file.getParentFile();
        prepare(file.toPath());
    }

    private void prepare(Path file) {
        clearMessages();
        Task<RestorePlan> task = new Task<>() {
            @Override protected RestorePlan call() throws StorageException {
                updateMessage("Memeriksa " + file.getFileName() + "...");
                return service.prepare(file);
            }
        };
        busy(task, "Memeriksa " + file.getFileName() + "...");
        task.setOnSucceeded(e -> {                                         // FX thread
            idle();
            plan = task.getValue();
            showPreview();
        });
        task.setOnFailed(e -> {
            idle();
            fail("Cadangan tidak bisa dipakai. " + message(task) + " Tidak ada data yang berubah.");
        });
        run(task, "restore-prepare");
    }

    // =========================================================================
    // step 3 and 4: preview and confirmation
    // =========================================================================

    private void showPreview() {
        BackupPreview bk = plan.preview();
        BackupPreview now = BackupPreview.of(backup.snapshot(true));

        Label heading = new Label("Isi cadangan");
        heading.getStyleClass().add("title-2");
        Label meta = new Label("Dibuat: " + bk.exportedAt().atZone(zone()).format(WHEN)
                + "\nVersi aplikasi: " + (bk.appVersion() == null ? "tidak tercatat" : bk.appVersion()) + " (format " + bk.formatVersion() + ")"
                + "\nFile: " + plan.source().getFileName());
        meta.getStyleClass().add("muted");

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(8);
        grid.add(header(""), 0, 0);
        grid.add(header("Saat ini"), 1, 0);
        grid.add(header("Cadangan"), 2, 0);
        int row = 1;
        row = compare(grid, row, "Transaksi", now.transactions(), bk.transactions());
        row = compare(grid, row, "Kolom kustom", now.customColumns(), bk.customColumns());
        row = compare(grid, row, "Kategori", now.categories(), bk.categories());
        row = compare(grid, row, "Badge", now.badges(), bk.badges());
        row = compare(grid, row, "Target tabungan", now.goals(), bk.goals());
        row = compare(grid, row, "Level", "Lv " + now.level() + " / " + now.xp() + " XP", "Lv " + bk.level() + " / " + bk.xp() + " XP",
                now.level() != bk.level() || now.xp() != bk.xp());
        row = compare(grid, row, "Donor", String.valueOf(now.recipients()),
                bk.hasDonorData() ? String.valueOf(bk.recipients()) : "tidak termasuk", now.recipients() != bk.recipients() && bk.hasDonorData());

        VBox notes = new VBox(4);
        if (!bk.hasDonorData()) notes.getChildren().add(note("Cadangan ini dibuat tanpa data donor: daftar donor saat ini dipertahankan."));
        if (!bk.hasReminderSettings()) notes.getChildren().add(note("Cadangan ini tidak berisi pengaturan pengingat: pengaturan saat ini dipertahankan."));
        notes.getChildren().add(note("Level, XP, dan badge dipulihkan apa adanya; pemulihan tidak memberi XP atau badge baru."));

        Label warning = new Label("Semua data saat ini akan DIGANTI dengan isi cadangan ini. Sebelum itu, salinan pengaman dari data saat ini "
                + "dibuat otomatis, sehingga pemulihan bisa dibatalkan dengan memulihkan salinan tersebut.");
        warning.setWrapText(true);
        warning.setPrefWidth(470);
        warning.setMinHeight(Region.USE_PREF_SIZE);                       // wrap onto as many lines as needed, never an ellipsis
        HBox warningRow = new HBox(12, IconCatalog.icon("alert", 28), warning);
        warningRow.getChildren().get(0).getStyleClass().add("icon-warning");
        warningRow.setAlignment(Pos.CENTER_LEFT);
        warningRow.getStyleClass().add("warning-box");

        TextField typed = new TextField();
        typed.setPromptText("Ketik " + CONFIRM_WORD + " untuk melanjutkan");
        Button replace = new Button("Ganti data saat ini", IconCatalog.icon("restore", 16));
        replace.getStyleClass().add("danger");
        replace.disableProperty().bind(typed.textProperty().isNotEqualTo(CONFIRM_WORD));   // an exact word, on purpose
        replace.setOnAction(e -> apply());
        Button cancel = new Button("Batal");
        cancel.setOnAction(e -> backToSource());
        HBox buttons = new HBox(10, replace, cancel);
        buttons.setAlignment(Pos.CENTER_LEFT);

        previewBox.getChildren().setAll(heading, meta, grid, notes, warningRow, typed, buttons);
        hide(sourceBox);
        show(previewBox);
        fitWindow();
    }

    private int compare(GridPane grid, int row, String label, int now, int backupValue) {
        return compare(grid, row, label, String.valueOf(now), String.valueOf(backupValue), now != backupValue);
    }

    private int compare(GridPane grid, int row, String label, String now, String backupValue, boolean differs) {
        Label name = new Label(label);
        name.getStyleClass().add("muted");
        Label a = new Label(now);
        Label b = new Label(backupValue);
        b.setStyle("-fx-font-weight: bold;");
        if (differs) b.getStyleClass().add("accent-text");
        grid.add(name, 0, row);
        grid.add(a, 1, row);
        grid.add(b, 2, row);
        return row + 1;
    }

    private void backToSource() {
        plan = null;
        clearMessages();
        hide(previewBox);
        show(sourceBox);
        autoList.getItems().setAll(autoBackups.list());
        fitWindow();
    }

    // =========================================================================
    // step 5: replace (background)
    // =========================================================================

    private void apply() {
        clearMessages();
        final String safetyJson;
        try {
            flushAll.run();                                                 // every pending save is on disk first
            safetyJson = backup.backupJson(true);                           // the snapshot is taken here, on the FX thread
        } catch (StorageException e) {
            fail("Cadangan pengaman tidak bisa dibuat, jadi data tidak diganti. " + e.getMessage());
            return;
        }
        RestorePlan chosen = plan;
        Task<RestoreResult> task = new Task<>() {
            @Override protected RestoreResult call() throws StorageException {
                updateMessage("Mengganti data...");
                return service.apply(chosen, safetyJson, p -> updateProgress(p, 1.0));
            }
        };
        previewBox.setDisable(true);
        busy(task, "Mengganti data. Jangan tutup aplikasi...");
        progress.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {                                         // FX thread
            progress.progressProperty().unbind();
            idle();
            previewBox.setDisable(false);
            RestoreResult result = task.getValue();
            showDone(result);
            onRestored.accept(result);                                      // toast and notification entry
        });
        task.setOnFailed(e -> {
            progress.progressProperty().unbind();
            idle();
            previewBox.setDisable(false);
            fail(message(task));
        });
        run(task, "restore-apply");
    }

    private void showDone(RestoreResult result) {
        Label ok = new Label("Data berhasil dipulihkan", IconCatalog.icon("check", 22));
        ok.getStyleClass().addAll("title-2", "income-text");
        ok.setGraphicTextGap(10);
        Label safety = new Label("Salinan pengaman data sebelumnya: " + result.safetyBackup().getFileName()
                + "\n(di folder 'backups' di folder data aplikasi)");
        safety.setWrapText(true);
        safety.setMaxWidth(520);
        Label hint = new Label("Semua tampilan sudah diperbarui. Jika hasilnya bukan yang kamu inginkan, pulihkan salinan pengaman tersebut dari daftar cadangan otomatis.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        doneBox.getChildren().setAll(ok, safety, hint);
        hide(previewBox);
        show(doneBox);
        fitWindow();
    }

    /** The dialog was sized for its first content; resize the window when the content changes so nothing is cut off. */
    private void fitWindow() {
        javafx.application.Platform.runLater(() -> {
            var scene = dialog.getDialogPane().getScene();
            if (scene != null && scene.getWindow() != null) scene.getWindow().sizeToScene();
        });
    }

    // =========================================================================
    // small helpers
    // =========================================================================

    private void busy(Task<?> task, String text) {
        busyText.setText(text);
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        show(busyBox);
        sourceBox.setDisable(true);
    }

    private void idle() {
        hide(busyBox);
        sourceBox.setDisable(false);
    }

    private void run(Task<?> task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.start();
    }

    private void fail(String text) {
        error.setText(text);
        show(error);
    }

    private void clearMessages() {
        error.setText("");
        hide(error);
    }

    private static String message(Task<?> task) {
        Throwable t = task.getException();
        String m = t == null ? null : t.getMessage();
        return m == null || m.isBlank() ? "Terjadi kesalahan yang tidak diketahui." : m;
    }

    private String describe(BackupFile f) {
        String kind = switch (f.kind()) {
            case AUTO -> "Otomatis";
            case SAFETY -> "Sebelum pemulihan";
            case OTHER -> "Lainnya";
        };
        return kind + "  -  " + f.modified().atZone(zone()).format(WHEN) + "  -  " + size(f.size());
    }

    private ZoneId zone() {
        return clock.getZone();
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static Label header(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("caption");
        return l;
    }

    private static Label note(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("caption");
        l.setWrapText(true);
        l.setMaxWidth(520);
        return l;
    }

    private static void hide(Node n) {
        n.setVisible(false);
        n.setManaged(false);
    }

    private static void show(Node n) {
        n.setVisible(true);
        n.setManaged(true);
    }
}
