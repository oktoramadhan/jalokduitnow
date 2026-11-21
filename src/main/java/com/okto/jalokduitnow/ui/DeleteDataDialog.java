package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.service.BackupService;
import com.okto.jalokduitnow.service.DeleteAction;
import com.okto.jalokduitnow.service.DeleteDataService;
import com.okto.jalokduitnow.service.DeleteDataService.DeleteResult;
import com.okto.jalokduitnow.service.DeletePreview;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * One confirmation dialog for the three delete actions. It states exactly what goes (with counts) and what stays, requires the
 * word DELETE, runs the deletion in a background Task with a progress bar, and reports the safety backup's file name.
 * All rules live in {@link DeleteDataService}; this class only drives it and shows messages.
 */
public final class DeleteDataDialog {
    private final DeleteAction action;
    private final DeleteDataService service;
    private final BackupService backup;
    private final Runnable flushAll;
    private final IntSupplier notificationCount;
    private final Consumer<DeleteResult> onDeleted;
    private final Consumer<DeleteResult> onRestoreIt;

    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox confirmBox = new VBox(14);
    private final HBox busyBox = new HBox(12);
    private final ProgressBar progress = new ProgressBar();
    private final Label busyText = new Label("Menghapus data. Jangan tutup aplikasi...");
    private final Label error = new Label();
    private final VBox doneBox = new VBox(12);

    private DeleteDataDialog(DeleteAction action, DeleteDataService service, BackupService backup, Runnable flushAll,
                             IntSupplier notificationCount, Consumer<DeleteResult> onDeleted, Consumer<DeleteResult> onRestoreIt) {
        this.action = action;
        this.service = service;
        this.backup = backup;
        this.flushAll = flushAll;
        this.notificationCount = notificationCount;
        this.onDeleted = onDeleted;
        this.onRestoreIt = onRestoreIt;
    }

    public static void show(DeleteAction action, DeleteDataService service, BackupService backup, Runnable flushAll,
                            IntSupplier notificationCount, Consumer<DeleteResult> onDeleted, Consumer<DeleteResult> onRestoreIt) {
        new DeleteDataDialog(action, service, backup, flushAll, notificationCount, onDeleted, onRestoreIt).open();
    }

    private void open() {
        dialog.setTitle(action.title());
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Tutup", ButtonBar.ButtonData.CANCEL_CLOSE));
        Theme.styleDialog(dialog.getDialogPane());

        DeletePreview preview;
        int backupFiles = 0;
        try {
            backupFiles = service.backupFileCount();
            preview = DeletePreview.of(action, backup.snapshot(true), notificationCount.getAsInt(), backupFiles);
        } catch (StorageException e) {
            Label fail = new Label(e.getMessage());
            fail.setWrapText(true);
            fail.setMaxWidth(520);
            fail.getStyleClass().add("error-label");
            dialog.getDialogPane().setContent(new VBox(12, new Label(action.title()), fail));
            dialog.showAndWait();
            return;
        }

        Label title = new Label(action.title());
        title.getStyleClass().add("title-2");
        confirmBox.getChildren().add(listBlock("Yang akan dihapus", preview.willDelete(), "alert", "icon-warning"));
        confirmBox.getChildren().add(listBlock("Yang tetap ada", preview.kept(), "check", "icon-white"));

        Label safety = new Label("Sebelum menghapus, cadangan pengaman (JSON lengkap) dibuat otomatis di folder 'backups'. "
                + "Namanya ditampilkan setelah selesai, dan kamu bisa memulihkannya dalam waktu singkat.");
        safety.getStyleClass().add("muted");
        safety.setWrapText(true);
        safety.setMinHeight(Region.USE_PREF_SIZE);
        safety.setPrefWidth(520);
        confirmBox.getChildren().add(safety);

        CheckBox alsoBackups = new CheckBox("Hapus juga cadangan yang ada");
        if (action == DeleteAction.FACTORY) {                                // only a factory reset may offer this; off by default
            Label warn = new Label(backupFiles == 0
                    ? "Belum ada cadangan otomatis yang bisa dihapus."
                    : "Peringatan: " + backupFiles + " file cadangan buatan aplikasi akan dihapus permanen, sehingga data lama tidak bisa "
                    + "dipulihkan dari cadangan tersebut. Cadangan pengaman yang baru dibuat tetap disimpan, dan file yang kamu simpan sendiri tidak disentuh.");
            warn.setWrapText(true);
            warn.setMinHeight(Region.USE_PREF_SIZE);
            warn.setPrefWidth(500);
            warn.getStyleClass().add("caption");
            alsoBackups.setDisable(backupFiles == 0);
            confirmBox.getChildren().add(new VBox(6, alsoBackups, warn));
        }

        TextField typed = new TextField();
        typed.setPromptText("Ketik " + DeleteDataService.CONFIRM_WORD + " untuk melanjutkan");
        Button confirm = new Button(action.title(), IconCatalog.icon("delete", 16));
        confirm.getStyleClass().add("danger");
        confirm.setDefaultButton(false);
        confirm.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> !DeleteDataService.isConfirmed(typed.getText()), typed.textProperty()));   // exact word, on purpose
        confirm.setOnAction(e -> start(alsoBackups.isSelected()));
        Button cancel = new Button("Batal");
        cancel.setOnAction(e -> dialog.close());
        HBox buttons = new HBox(10, confirm, cancel);
        buttons.setAlignment(Pos.CENTER_LEFT);
        confirmBox.getChildren().addAll(typed, buttons);

        progress.setPrefWidth(220);
        busyBox.setAlignment(Pos.CENTER_LEFT);
        busyBox.getChildren().addAll(progress, busyText);
        hide(busyBox);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(520);
        error.setMinHeight(Region.USE_PREF_SIZE);
        hide(error);
        hide(doneBox);

        VBox content = new VBox(16, title, confirmBox, busyBox, error, doneBox);
        content.setPadding(new Insets(8));
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void start(boolean alsoDeleteBackups) {
        hide(error);
        final String json;
        try {
            flushAll.run();                                                  // pending saves reach the disk first
            json = backup.backupJson(true);                                  // snapshot on the FX thread
        } catch (StorageException e) {
            fail("Cadangan pengaman tidak bisa dibuat, jadi tidak ada data yang dihapus. " + e.getMessage());
            return;
        }
        Task<DeleteResult> task = new Task<>() {
            @Override protected DeleteResult call() throws StorageException {
                return service.execute(action, alsoDeleteBackups, json, p -> updateProgress(p, 1.0));
            }
        };
        confirmBox.setDisable(true);
        progress.progressProperty().bind(task.progressProperty());
        show(busyBox);
        task.setOnSucceeded(e -> {                                           // FX thread
            progress.progressProperty().unbind();
            hide(busyBox);
            DeleteResult result = task.getValue();
            showDone(result);
            onDeleted.accept(result);
        });
        task.setOnFailed(e -> {
            progress.progressProperty().unbind();
            hide(busyBox);
            confirmBox.setDisable(false);
            Throwable t = task.getException();
            String m = t == null || t.getMessage() == null || t.getMessage().isBlank() ? "Terjadi kesalahan yang tidak diketahui." : t.getMessage();
            fail(m + " Data tetap seperti semula.");
        });
        Thread thread = new Thread(task, "delete-data");
        thread.setDaemon(true);
        thread.start();
    }

    private void showDone(DeleteResult result) {
        Label ok = new Label(action.title() + " selesai", IconCatalog.icon("check", 22));
        ok.getStyleClass().addAll("title-2", "income-text");
        ok.setGraphicTextGap(10);
        ok.setWrapText(true);
        Label safety = new Label("Cadangan pengaman: " + result.safetyBackup().getFileName() + "\n(di folder 'backups' di folder data aplikasi)"
                + (result.backupsDeleted() > 0 ? "\n" + result.backupsDeleted() + " cadangan lama dihapus." : ""));
        safety.setWrapText(true);
        safety.setMaxWidth(520);
        safety.setMinHeight(Region.USE_PREF_SIZE);
        Label hint = new Label("Semua tampilan sudah diperbarui. Tombol pemulihan ini tersedia selama "
                + DeleteDataService.RESTORE_WINDOW.toMinutes() + " menit; setelah itu pulihkan dari daftar cadangan di Pengaturan.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        Button restoreIt = new Button("Pulihkan cadangan ini", IconCatalog.icon("restore", 16));
        restoreIt.getStyleClass().add("primary");
        restoreIt.setOnAction(e -> {
            dialog.close();
            onRestoreIt.accept(result);
        });
        doneBox.getChildren().setAll(ok, safety, hint, restoreIt);
        hide(confirmBox);
        show(doneBox);
        javafx.application.Platform.runLater(() -> {
            var scene = dialog.getDialogPane().getScene();
            if (scene != null && scene.getWindow() != null) scene.getWindow().sizeToScene();
        });
    }

    private static Node listBlock(String heading, java.util.List<String> lines, String icon, String iconClass) {
        Label h = new Label(heading);
        h.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(6, h);
        for (String line : lines) {
            Node ic = IconCatalog.icon(icon, 14);
            ic.getStyleClass().add(iconClass);
            Label l = new Label(line);
            l.setWrapText(true);
            l.setMinHeight(Region.USE_PREF_SIZE);
            l.setPrefWidth(500);
            HBox row = new HBox(8, ic, l);
            row.setAlignment(Pos.TOP_LEFT);
            box.getChildren().add(row);
        }
        return box;
    }

    private void fail(String text) {
        error.setText(text);
        show(error);
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
