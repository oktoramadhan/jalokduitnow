package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.FinanceService.MonthSummary;
import com.okto.jalokduitnow.service.SchemaService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Map;
import java.util.UUID;

/** Right pane: details of the selected transaction (Edit / Delete / Share), or the monthly overview. */
public class DetailPanel extends VBox {
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Formats.ID);

    private final FinanceService finance;
    private final CategoryService categories;
    private final SchemaService schemaService;
    private final UiState state;
    private final TransactionActions actions;

    public DetailPanel(FinanceService finance, CategoryService categories, SchemaService schemaService,
                       UiState state, TransactionActions actions) {
        this.finance = finance;
        this.categories = categories;
        this.schemaService = schemaService;
        this.state = state;
        this.actions = actions;

        getStyleClass().add("card");
        setSpacing(16);
        setMinWidth(320);
        setPrefWidth(320);
        setMaxWidth(320);

        state.selected.addListener((o, a, b) -> render());
        state.period.addListener((o, a, b) -> render());
        finance.addListener(this::render);
        categories.addListener(this::render);
        schemaService.addListener(this::render);
        render();
    }

    private void render() {
        getChildren().clear();
        Transaction t = state.selected.get();
        if (t == null) renderOverview(); else renderDetails(t);
    }

    // ---- selected transaction ----

    private void renderDetails(Transaction t) {
        Label title = new Label("Detail Transaksi");
        title.getStyleClass().add("title-2");
        Button close = new Button();
        close.setGraphic(IconCatalog.icon("close", 16));
        close.getStyleClass().addAll("ghost", "close");
        close.setOnAction(e -> state.selected.set(null));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(title, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);

        Category category = categories.find(t.getCategoryId()).orElse(null);
        Label amount = new Label(Formats.signedRupiah(t.signedAmount()));
        amount.getStyleClass().addAll("value-big", t.signedAmount() > 0 ? "income-text" : "expense-text");
        Label typeText = new Label(t.typeLabel());
        HBox pill = new HBox(typeText);
        pill.getStyleClass().addAll("pill", t.signedAmount() > 0 ? "income" : "expense");
        pill.setMaxWidth(Region.USE_PREF_SIZE);

        Label categoryName = new Label(category == null ? "?" : category.getName());
        categoryName.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        HBox categoryRow = new HBox(12, category == null ? new Label("•") : Tiles.tile(category, 44), categoryName);
        categoryRow.setAlignment(Pos.CENTER_LEFT);

        VBox rows = new VBox(12);
        rows.getChildren().add(row("Tanggal", new Label(t.getDate().format(LONG_DATE))));
        for (ColumnDefinition col : schemaService.getSchema().visibleColumns()) {
            String shown = col.getType().displayValue(t.getCustomValue(col.getId()));
            rows.getChildren().add(row(col.getName(), valueLabel(shown.isEmpty() ? "-" : shown)));
        }

        Button edit = new Button("Ubah", IconCatalog.icon("edit", 16));
        edit.getStyleClass().add("primary");
        edit.setOnAction(e -> actions.edit(t));
        Button delete = new Button("Hapus", IconCatalog.icon("delete", 16));
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> actions.delete(t));
        Button share = new Button("Bagikan", IconCatalog.icon("share", 16));
        share.setDisable(true); // the Sharer feature comes in a later stage
        Tooltip.install(share, new Tooltip("Segera hadir"));
        HBox buttons = new HBox(8, edit, delete, share);
        buttons.setAlignment(Pos.CENTER_LEFT);
        for (Button b : new Button[]{edit, delete, share}) Motion.grow(b);

        getChildren().addAll(header, categoryRow, amount, pill, divider(), rows, buttons);
    }

    // ---- monthly overview ----

    private void renderOverview() {
        YearMonth ym = state.period.get();
        MonthSummary s = finance.summaryFor(ym);

        Label title = new Label("Ringkasan Bulanan");
        title.getStyleClass().add("title-2");
        Label month = new Label(ym.getMonth().getDisplayName(TextStyle.FULL, Formats.ID) + " " + ym.getYear());
        month.getStyleClass().add("muted");

        Label balance = new Label(Formats.rupiah(s.balance()));
        balance.getStyleClass().addAll("value-big");
        if (s.balance() != 0) balance.getStyleClass().add(s.balance() > 0 ? "income-text" : "expense-text");
        Label balanceCaption = new Label("Saldo bulan ini");
        balanceCaption.getStyleClass().add("caption");

        long count = finance.getAll().stream().filter(t -> YearMonth.from(t.getDate()).equals(ym)).count();
        VBox rows = new VBox(12);
        rows.getChildren().add(row("Pemasukan", colored(Formats.rupiah(s.income()), "income-text")));
        rows.getChildren().add(row("Pengeluaran", colored(Formats.rupiah(s.expense()), "expense-text")));
        rows.getChildren().add(row("Jumlah transaksi", valueLabel(String.valueOf(count))));
        if (s.income() > 0) {
            long saved = Math.round(100.0 * s.balance() / s.income());
            rows.getChildren().add(row("Tingkat tabungan", valueLabel(saved + "%")));
        }

        getChildren().addAll(new VBox(2, title, month), new VBox(2, balance, balanceCaption), divider(), rows);

        Map<UUID, Long> byCategory = finance.expenseByCategory(ym);
        if (!byCategory.isEmpty()) {
            Label top = new Label("Pengeluaran Terbesar");
            top.getStyleClass().add("title-2");
            VBox list = new VBox(14);
            byCategory.entrySet().stream().limit(3).forEach(e -> categories.find(e.getKey()).ifPresent(c -> {
                Label name = new Label(c.getName());
                name.setStyle("-fx-font-weight: bold;");
                Label amount = new Label(Formats.rupiah(e.getValue()));
                amount.getStyleClass().add("muted");
                ProgressBar share = new ProgressBar((double) e.getValue() / Math.max(1, s.expense()));
                share.setMaxWidth(Double.MAX_VALUE);
                HBox line = new HBox(12, Tiles.tile(c, 36), new VBox(2, name, amount));
                line.setAlignment(Pos.CENTER_LEFT);
                list.getChildren().add(new VBox(8, line, share));
            }));
            getChildren().addAll(divider(), top, list);
        } else {
            Label empty = new Label("Pilih transaksi untuk melihat detailnya.");
            empty.getStyleClass().add("caption");
            empty.setWrapText(true);
            getChildren().addAll(divider(), empty);
        }
    }

    // ---- helpers ----

    private static HBox row(String label, Node value) {
        Label l = new Label(label);
        l.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, l, spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label valueLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        l.setWrapText(true);
        l.setMaxWidth(180);
        l.setAlignment(Pos.CENTER_RIGHT);
        return l;
    }

    private static Label colored(String text, String styleClass) {
        Label l = valueLabel(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    private static Region divider() {
        Region r = new Region();
        r.getStyleClass().add("divider");
        return r;
    }
}
