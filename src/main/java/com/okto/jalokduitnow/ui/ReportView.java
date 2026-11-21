package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.exception.ShareException;
import com.okto.jalokduitnow.model.Recipient;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.report.*;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.service.*;
import com.okto.jalokduitnow.share.ClipboardSharer;
import com.okto.jalokduitnow.share.WhatsAppSharer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The Laporan page: pick a month, a report type (personal or donor), a recipient and options; the preview
 * updates as you change anything; then send it through WhatsApp or copy it. Nothing is sent without the user
 * pressing Send in WhatsApp itself, and a report with transaction details asks for confirmation first.
 */
public class ReportView extends HBox {
    private enum ReportType {
        PERSONAL("Pribadi"), DONOR("Donor");

        final String label;

        ReportType(String label) { this.label = label; }
    }

    private final FinanceService finance;
    private final CategoryService categories;
    private final SchemaService schemaService;
    private final RecipientService recipients;
    private final DonorSettings donorSettings;
    private final UiState state;
    private final GameEventBus bus;
    private final Clock clock;
    private final WhatsAppSharer whatsApp;
    private final ClipboardSharer clipboard;

    private final Label periodLabel = new Label();
    private final ComboBox<ReportType> typeBox = new ComboBox<>(FXCollections.observableArrayList(ReportType.values()));
    private final ComboBox<Recipient> recipientBox = new ComboBox<>();
    private final CheckBox details = new CheckBox("Sertakan detail transaksi");
    private final ComboBox<ColumnDefinition> breakdownBox = new ComboBox<>();
    private final Label donorHint = new Label();
    private final TextArea preview = new TextArea();
    private final Button send = new Button();
    private final Button copy = new Button("Salin", IconCatalog.icon("copy", 16));
    private final Label status = new Label();
    private boolean refreshingLists;

    public ReportView(FinanceService finance, CategoryService categories, SchemaService schemaService,
                      RecipientService recipients, DonorSettings donorSettings, UiState state, GameEventBus bus,
                      Clock clock, WhatsAppSharer whatsApp, ClipboardSharer clipboard) {
        this.finance = finance;
        this.categories = categories;
        this.schemaService = schemaService;
        this.recipients = recipients;
        this.donorSettings = donorSettings;
        this.state = state;
        this.bus = bus;
        this.clock = clock;
        this.whatsApp = whatsApp;
        this.clipboard = clipboard;

        setSpacing(20);
        VBox controls = buildControls();
        VBox previewCard = buildPreview();
        HBox.setHgrow(previewCard, Priority.ALWAYS);
        getChildren().addAll(controls, previewCard);

        typeBox.setValue(ReportType.PERSONAL);
        recipients.addListener(this::refreshLists);
        schemaService.addListener(this::refreshLists);
        categories.addListener(this::render);
        finance.addListener(this::render);
        donorSettings.addListener(this::render);
        state.period.addListener((o, a, b) -> render());
        refreshLists();
    }

    // ---- layout ----

    private VBox buildControls() {
        Label title = new Label("Atur Laporan");
        title.getStyleClass().add("title-2");

        Button prev = iconButton("left", "Bulan sebelumnya", () -> state.period.set(state.period.get().minusMonths(1)));
        Button next = iconButton("right", "Bulan berikutnya", () -> state.period.set(state.period.get().plusMonths(1)));
        periodLabel.setStyle("-fx-font-weight: bold;");
        periodLabel.setMaxWidth(Double.MAX_VALUE);
        periodLabel.setAlignment(Pos.CENTER);
        HBox.setHgrow(periodLabel, Priority.ALWAYS);
        HBox period = new HBox(6, prev, periodLabel, next);
        period.setAlignment(Pos.CENTER);

        typeBox.setConverter(converter(t -> t.label));
        typeBox.setMaxWidth(Double.MAX_VALUE);
        typeBox.setOnAction(e -> {
            if (typeBox.getValue() == ReportType.DONOR && recipientBox.getValue() == null) {
                recipients.getDefault().ifPresent(recipientBox::setValue);   // donors are usually sent to the default recipient
            }
            render();
        });

        recipientBox.setConverter(converter(Recipient::getName));
        recipientBox.setPromptText("Tanpa penerima (pilih di WhatsApp)");
        recipientBox.setMaxWidth(Double.MAX_VALUE);
        recipientBox.setOnAction(e -> {
            if (!refreshingLists) render();
        });
        Button clearRecipient = iconButton("close", "Hapus pilihan penerima", () -> recipientBox.setValue(null));
        HBox recipientRow = new HBox(6, recipientBox, clearRecipient);
        HBox.setHgrow(recipientBox, Priority.ALWAYS);
        recipientRow.setAlignment(Pos.CENTER_LEFT);

        details.setOnAction(e -> render());
        Label detailsHint = new Label("Menambahkan tanggal, kategori, jumlah, dan catatan setiap transaksi.");
        detailsHint.getStyleClass().add("caption");
        detailsHint.setWrapText(true);

        breakdownBox.setConverter(converter(ColumnDefinition::getName));
        breakdownBox.setPromptText("Tanpa rincian");
        breakdownBox.setMaxWidth(Double.MAX_VALUE);
        breakdownBox.setOnAction(e -> {
            if (!refreshingLists) render();
        });
        Button clearBreakdown = iconButton("close", "Hapus pilihan rincian", () -> breakdownBox.setValue(null));
        HBox breakdownRow = new HBox(6, breakdownBox, clearBreakdown);
        HBox.setHgrow(breakdownBox, Priority.ALWAYS);
        breakdownRow.setAlignment(Pos.CENTER_LEFT);

        donorHint.getStyleClass().add("caption");
        donorHint.setWrapText(true);
        donorHint.managedProperty().bind(donorHint.visibleProperty());

        send.getStyleClass().add("primary");
        send.setGraphic(IconCatalog.icon("send", 16));
        send.setMaxWidth(Double.MAX_VALUE);
        send.setWrapText(true);
        send.setOnAction(e -> sendWhatsApp());
        copy.setMaxWidth(Double.MAX_VALUE);
        copy.setOnAction(e -> copyToClipboard());

        Label note = new Label("WhatsApp akan terbuka dengan pesan yang sudah siap. Kamu tetap perlu menekan tombol Kirim di WhatsApp.");
        note.getStyleClass().add("caption");
        note.setWrapText(true);
        status.getStyleClass().add("caption");
        status.setWrapText(true);

        VBox card = new VBox(14, title, labeled("Periode", period), labeled("Jenis laporan", typeBox),
                labeled("Penerima", recipientRow), labeled("Rincian pilihan", breakdownRow),
                new VBox(4, details, detailsHint), donorHint, send, copy, note, status);
        card.getStyleClass().add("card");
        card.setMinWidth(340);
        card.setPrefWidth(340);
        card.setMaxWidth(340);
        card.setPadding(new Insets(20));
        return card;
    }

    private VBox buildPreview() {
        Label title = new Label("Pratinjau");
        title.getStyleClass().add("title-2");
        Label sub = new Label("Teks yang akan dikirim");
        sub.getStyleClass().add("muted");
        preview.setEditable(false);
        preview.setWrapText(true);
        VBox.setVgrow(preview, Priority.ALWAYS);
        VBox card = new VBox(12, new HBox(12, title, sub), preview);
        ((HBox) card.getChildren().get(0)).setAlignment(Pos.BASELINE_LEFT);
        card.getStyleClass().add("card");
        return card;
    }

    private static VBox labeled(String text, javafx.scene.Node control) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        return new VBox(6, l, control);
    }

    // ---- data -> controls ----

    /** Reloads the recipient and column choices, keeping what is selected if it still exists. */
    private void refreshLists() {
        refreshingLists = true;
        Recipient selected = recipientBox.getValue();
        recipientBox.setItems(FXCollections.observableArrayList(recipients.getAll()));
        if (selected != null) recipients.find(selected.getId()).ifPresent(recipientBox::setValue);
        else if (typeBox.getValue() == ReportType.DONOR) recipients.getDefault().ifPresent(recipientBox::setValue);

        ColumnDefinition column = breakdownBox.getValue();
        List<ColumnDefinition> choiceColumns = schemaService.getSchema().visibleColumns().stream()
                .filter(c -> !c.getType().options().isEmpty()).toList();
        breakdownBox.setItems(FXCollections.observableArrayList(choiceColumns));
        if (column != null && choiceColumns.stream().anyMatch(c -> c.getId().equals(column.getId()))) {
            breakdownBox.setValue(choiceColumns.stream().filter(c -> c.getId().equals(column.getId())).findFirst().orElse(null));
        }
        refreshingLists = false;
        render();
    }

    private ReportGenerator currentGenerator() {
        ColumnDefinition column = breakdownBox.getValue();
        ReportOptions options = new ReportOptions(details.isSelected(), column == null ? null : column.getId());
        Recipient r = recipientBox.getValue();
        if (typeBox.getValue() == ReportType.DONOR) {
            return new DonorReportGenerator(finance, categories, schemaService, donorSettings,
                    r == null ? "" : r.getName(), clock, options);
        }
        return new PersonalReportGenerator(finance, categories, schemaService, clock, options);
    }

    private void render() {
        YearMonth month = state.period.get();
        periodLabel.setText(month.getMonth().getDisplayName(TextStyle.FULL, Formats.ID) + " " + month.getYear());
        preview.setText(currentGenerator().generate(month));

        Recipient r = recipientBox.getValue();
        send.setText(r == null ? "Kirim via WhatsApp" : "Kirim ke " + r.getName() + " via WhatsApp");
        boolean donorType = typeBox.getValue() == ReportType.DONOR;
        donorHint.setVisible(donorType && donorSettings.donorCategoryIds().isEmpty());
        donorHint.setText("Belum ada kategori pemasukan donor. Pilih di Pengaturan, bagian Donor, agar dana diterima terhitung.");
        status.setText("");
    }

    // ---- sharing ----

    private void sendWhatsApp() {
        YearMonth month = state.period.get();
        String text = currentGenerator().generate(month);
        Recipient r = recipientBox.getValue();
        if (details.isSelected() && !confirmDetails(month, r)) return;

        WhatsAppSharer sharer = whatsApp.forNumber(r == null ? null : r.getPhoneNumber());
        boolean donor = typeBox.getValue() == ReportType.DONOR;
        send.setDisable(true);
        // opening a browser can take a moment: do it off the FX thread and come back with Platform.runLater
        CompletableFuture.runAsync(() -> {
            try {
                sharer.share(text);
                Platform.runLater(() -> {
                    send.setDisable(false);
                    status.setText("WhatsApp dibuka dengan pesan siap kirim" + (r == null ? "." : " untuk " + r.getName() + ".")
                            + " Tekan Kirim di WhatsApp untuk mengirimnya.");
                    if (donor) bus.publish(new GameEvent.DonorReportShared("WhatsApp"));
                });
            } catch (ShareException e) {
                Platform.runLater(() -> {
                    send.setDisable(false);
                    status.setText("");
                    showError(e.getMessage());
                });
            }
        });
    }

    private void copyToClipboard() {
        try {
            clipboard.share(preview.getText());
            status.setText("Laporan disalin ke clipboard.");
            if (typeBox.getValue() == ReportType.DONOR) bus.publish(new GameEvent.DonorReportShared("Clipboard"));
        } catch (ShareException e) {
            showError(e.getMessage());
        }
    }

    /** States exactly what will be shared before a report with transaction details leaves the app. */
    private boolean confirmDetails(YearMonth month, Recipient r) {
        long count = finance.getAll().stream().filter((Transaction t) -> YearMonth.from(t.getDate()).equals(month)).count();
        String listed = count > AbstractReportGenerator.MAX_DETAIL_LINES
                ? " (hanya " + AbstractReportGenerator.MAX_DETAIL_LINES + " yang pertama dicantumkan satu per satu)" : "";
        String to = r == null ? "dibuka di WhatsApp tanpa penerima tertentu (kamu memilih sendiri)"
                : "dikirim ke " + r.getName() + " (" + r.displayPhone() + ")";
        ButtonType share = new ButtonType("Lanjutkan", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Batal", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "Laporan ini menyertakan detail transaksi bulan ini: " + count + " transaksi" + listed + ", masing-masing dengan "
                        + "tanggal, kategori, jumlah, dan isi kolom kustom yang tampil (misalnya catatan).\n\n"
                        + "Laporan akan " + to + ".\n\nPastikan tidak ada yang tidak ingin kamu bagikan.",
                share, cancel);
        alert.setHeaderText("Bagikan detail transaksi?");
        alert.setGraphic(ColumnEditDialog.warningIcon());
        Theme.styleDialog(alert.getDialogPane());
        ((Button) alert.getDialogPane().lookupButton(share)).getStyleClass().add("primary");
        return alert.showAndWait().filter(b -> b == share).isPresent();
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        Theme.styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    // ---- helpers ----

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : text.apply(value); }
            @Override public T fromString(String s) { return null; }
        };
    }

    private static Button iconButton(String iconKey, String tip, Runnable action) {
        Button b = new Button();
        b.setGraphic(IconCatalog.icon(iconKey, 16));
        b.getStyleClass().add("ghost");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> action.run());
        return b;
    }
}
