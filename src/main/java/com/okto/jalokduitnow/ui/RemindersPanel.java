package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidSettingException;
import com.okto.jalokduitnow.reminder.ReminderKind;
import com.okto.jalokduitnow.reminder.ReminderSettings;
import com.okto.jalokduitnow.service.ReminderSettingsService;
import com.okto.jalokduitnow.thread.ReminderService;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * The "Pengingat" card in Settings: a switch and the options of every reminder (time, day of month, warning
 * percentage), quiet hours, the system tray switch and a test button. Every change goes through
 * ReminderSettingsService, which validates; a rejected value shows its message and the controls snap back.
 */
public class RemindersPanel extends VBox {
    private final ReminderSettingsService service;
    private final ReminderService reminders;
    private final boolean traySupported;
    private final Label error = new Label();
    private final Label testStatus = new Label();
    private boolean refreshing;

    private final java.util.Map<ReminderKind, CheckBox> enabledBoxes = new java.util.EnumMap<>(ReminderKind.class);
    private final TimeField dailyTime = new TimeField();
    private final TimeField streakTime = new TimeField();
    private final ComboBox<Integer> donorDay = numberBox(1, 31);
    private final TimeField donorTime = new TimeField();
    private final ComboBox<Integer> warnPercent = numberBox(50, 99);
    private final CheckBox quietOn = new CheckBox("Aktifkan jam tenang");
    private final TimeField quietStart = new TimeField();
    private final TimeField quietEnd = new TimeField();
    private final CheckBox trayOn = new CheckBox("Tampilkan juga sebagai notifikasi sistem");

    public RemindersPanel(ReminderSettingsService service, ReminderService reminders, boolean traySupported) {
        this.service = service;
        this.reminders = reminders;
        this.traySupported = traySupported;
        setSpacing(16);
        getStyleClass().add("card");
        setMaxWidth(620);

        Label title = new Label("Pengingat");
        title.getStyleClass().add("title-2");
        Label hint = new Label("Pengingat yang ramah, tidak pernah menyalahkan. Semuanya bisa dimatikan kapan saja.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        getChildren().addAll(title, hint,
                reminderBlock(ReminderKind.DAILY_LOG, option("Jam", dailyTime)),
                reminderBlock(ReminderKind.STREAK_RISK, option("Jam", streakTime)),
                reminderBlock(ReminderKind.DONOR_REPORT,
                        row(option("Tanggal", donorDay), option("Jam", donorTime)),
                        caption("Jika bulan itu lebih pendek (misalnya tanggal 31 di Februari), dipakai hari terakhir bulan tersebut.")),
                reminderBlock(ReminderKind.BUDGET, row(option("Peringatan di", warnPercent), new Label("%")),
                        caption("Pemberitahuan kedua muncul saat budget 100% tercapai.")),
                divider(), quietBlock(), trayBlock(), divider(), testBlock(), error);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);

        wire();
        service.addListener(this::refresh);
        refresh();
    }

    // ---- building ----

    private Node reminderBlock(ReminderKind kind, Node... options) {
        CheckBox on = new CheckBox();
        enabledBoxes.put(kind, on);
        Label name = new Label(kind.title());
        name.setStyle("-fx-font-weight: bold;");
        Label description = caption(kind.description());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(12, new VBox(2, name, description), spacer, on);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox block = new VBox(10, head);
        block.getChildren().addAll(options);
        return block;
    }

    private Node quietBlock() {
        Label name = new Label("Jam tenang");
        name.setStyle("-fx-font-weight: bold;");
        Label description = caption("Tidak ada pengingat di rentang ini. Pengingat yang jatuh tempo muncul setelah jam tenang selesai, selama harinya belum berganti.");
        return new VBox(10, name, description, quietOn, row(option("Mulai", quietStart), option("Selesai", quietEnd)));
    }

    private Node trayBlock() {
        Label name = new Label("Notifikasi sistem");
        name.setStyle("-fx-font-weight: bold;");
        VBox block = new VBox(10, name, trayOn);
        if (!traySupported) {
            trayOn.setDisable(true);
            block.getChildren().add(caption("Tidak didukung di sistem ini. Notifikasi tetap muncul di dalam aplikasi."));
        }
        return block;
    }

    private Node testBlock() {
        Button test = new Button("Kirim notifikasi uji", IconCatalog.icon("bell", 16));
        test.setOnAction(e -> {
            reminders.sendTest();
            testStatus.setText("Notifikasi uji dikirim. Lihat ikon lonceng di bagian atas.");
        });
        testStatus.getStyleClass().add("caption");
        testStatus.setWrapText(true);
        return new VBox(8, test, testStatus);
    }

    // ---- reading and writing settings ----

    private void wire() {
        for (ReminderKind kind : ReminderKind.values()) {
            enabledBoxes.get(kind).selectedProperty().addListener((o, was, now) -> apply(() -> service.setEnabled(kind, now)));
        }
        dailyTime.setOnChange(t -> apply(() -> service.setDailyLogTime(t)));
        streakTime.setOnChange(t -> apply(() -> service.setStreakRiskTime(t)));
        donorDay.setOnAction(e -> applyDonor());
        donorTime.setOnChange(t -> applyDonor());
        warnPercent.setOnAction(e -> {
            if (!refreshing && warnPercent.getValue() != null) apply(() -> service.setBudgetWarnPercent(warnPercent.getValue()));
        });
        quietOn.selectedProperty().addListener((o, was, now) -> applyQuiet());
        quietStart.setOnChange(t -> applyQuiet());
        quietEnd.setOnChange(t -> applyQuiet());
        trayOn.selectedProperty().addListener((o, was, now) -> apply(() -> service.setTrayEnabled(now)));
    }

    private void applyDonor() {
        if (refreshing || donorDay.getValue() == null) return;
        apply(() -> service.setDonorReport(donorDay.getValue(), donorTime.getTime()));
    }

    private void applyQuiet() {
        apply(() -> service.setQuietHours(quietOn.isSelected(), quietStart.getTime(), quietEnd.getTime()));
    }

    @FunctionalInterface
    private interface Change { void run() throws InvalidSettingException; }

    private void apply(Change change) {
        if (refreshing) return;
        try {
            change.run();
            error.setText("");
        } catch (InvalidSettingException e) {
            error.setText(e.getMessage());
            refresh();                      // snap the controls back to what is really saved
        }
    }

    /** Puts the saved settings into the controls without triggering changes. */
    private void refresh() {
        refreshing = true;
        ReminderSettings s = service.current();
        for (ReminderKind kind : ReminderKind.values()) enabledBoxes.get(kind).setSelected(s.isEnabled(kind));
        dailyTime.setTime(s.dailyLogTime());
        streakTime.setTime(s.streakRiskTime());
        donorDay.setValue(s.donorReportDay());
        donorTime.setTime(s.donorReportTime());
        warnPercent.setValue(s.budgetWarnPercent());
        quietOn.setSelected(s.isQuietEnabled());
        quietStart.setTime(s.quietStart());
        quietEnd.setTime(s.quietEnd());
        trayOn.setSelected(s.isTrayEnabled() && traySupported);
        refreshing = false;
    }

    // ---- small layout helpers ----

    private static ComboBox<Integer> numberBox(int from, int to) {
        List<Integer> values = new ArrayList<>();
        for (int i = from; i <= to; i++) values.add(i);
        ComboBox<Integer> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setPrefWidth(90);
        return box;
    }

    private static HBox option(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("muted");
        l.setMinWidth(90);
        HBox box = new HBox(10, l, control);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static HBox row(Node... nodes) {
        HBox box = new HBox(24, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Label caption(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("caption");
        l.setWrapText(true);
        return l;
    }

    private static Region divider() {
        Region r = new Region();
        r.getStyleClass().add("divider");
        return r;
    }
}
