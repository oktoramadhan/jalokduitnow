package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.FinanceService.MonthSummary;
import com.okto.jalokduitnow.service.GameService;
import com.okto.jalokduitnow.service.PeriodRange;
import com.okto.jalokduitnow.model.MonthlyScore;
import com.okto.jalokduitnow.service.SchemaService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.Clock;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Home page (center pane): month picker, Income/Expense/Balance cards, charts, category cards and the
 * latest transactions of the chosen month. The chosen month is shared through UiState.period.
 */
public class DashboardView extends VBox {
    private static final int RECENT_LIMIT = 8;

    private final FinanceService finance;
    private final CategoryService categories;
    private final UiState state;
    private final GameService game;
    private final Clock clock;
    private final HBox gradeChip = new HBox(8);     // quiet text, styled by the "meta" class
    private final HBox streakChip = new HBox(8);

    private final ComboBox<Month> monthBox = new ComboBox<>(FXCollections.observableArrayList(Month.values()));
    private final ComboBox<Integer> yearBox = new ComboBox<>();
    private final Label income = new Label(), expense = new Label(), balance = new Label();
    private final PieChart pie = new PieChart();
    private final BarChart<String, Number> bar = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final FlowPane categoryGrid = new FlowPane(16, 16);
    private final TransactionTable recent;
    private boolean updating;      // ignore combo events while we set them from code
    private boolean initialized;   // first refresh jumps to the latest month that has data

    public DashboardView(FinanceService finance, CategoryService categories, SchemaService schemaService,
                         UiState state, TransactionActions actions, GameService game, Clock clock, Runnable onSeeAll) {
        this.finance = finance;
        this.categories = categories;
        this.state = state;
        this.game = game;
        this.clock = clock;
        this.recent = new TransactionTable(categories, schemaService, state, actions, true);

        setSpacing(24);

        monthBox.setConverter(new StringConverter<>() {
            @Override public String toString(Month m) { return m == null ? "" : m.getDisplayName(TextStyle.FULL, Formats.ID); }
            @Override public Month fromString(String s) { return null; }
        });
        monthBox.getStyleClass().add("on-page");
        yearBox.getStyleClass().add("on-page");
        monthBox.setOnAction(e -> pickPeriod());
        yearBox.setOnAction(e -> pickPeriod());
        Label periodLabel = new Label("Periode");
        periodLabel.getStyleClass().add("muted");
        Region pickerSpacer = new Region();
        HBox.setHgrow(pickerSpacer, Priority.ALWAYS);
        gradeChip.setAlignment(Pos.CENTER);
        streakChip.setAlignment(Pos.CENTER);
        HBox picker = new HBox(12, periodLabel, monthBox, yearBox, pickerSpacer, gradeChip, streakChip);
        picker.setAlignment(Pos.CENTER_LEFT);

        HBox stats = statStrip(
                statCell("Pemasukan", income, "income-text"),
                statCell("Pengeluaran", expense, "expense-text"),
                statCell("Saldo", balance, null));

        pie.setLegendVisible(false);
        pie.setLabelsVisible(false);
        pie.setAnimated(false);
        pie.setMinHeight(280);
        pie.setPrefHeight(280);
        bar.setAnimated(false);
        ((NumberAxis) bar.getYAxis()).setTickLabelFormatter(new StringConverter<>() {   // 1500000 -> "1,5 jt"
            @Override public String toString(Number n) { return compactRupiah(n.doubleValue()); }
            @Override public Number fromString(String s) { return 0; }
        });
        bar.setLegendSide(javafx.geometry.Side.BOTTOM);
        bar.setMinHeight(280);
        bar.setPrefHeight(280);
        VBox pieCard = card("Pengeluaran per kategori", pie);
        VBox barCard = card("6 bulan terakhir", bar);
        HBox.setHgrow(pieCard, Priority.NEVER);      // the bar chart is the main chart and takes the spare width
        HBox.setHgrow(barCard, Priority.ALWAYS);
        pieCard.setPrefWidth(300);
        pieCard.setMinWidth(260);
        pieCard.setMaxWidth(340);
        barCard.setPrefWidth(620);
        HBox charts = new HBox(16, pieCard, barCard);

        Label gridTitle = new Label("Kategori bulan ini");
        gridTitle.getStyleClass().add("title-2");

        Label recentTitle = new Label("Transaksi terbaru");
        recentTitle.getStyleClass().add("title-2");
        Button seeAll = new Button("Lihat semua");
        seeAll.getStyleClass().add("ghost");
        seeAll.setOnAction(e -> onSeeAll.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox recentHeader = new HBox(recentTitle, spacer, seeAll);
        recentHeader.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(picker, stats, charts, gridTitle, categoryGrid, recentHeader, recent);

        finance.addListener(this::refresh);
        categories.addListener(this::refresh);
        state.search.addListener((o, a, b) -> render());
        game.addListener(this::renderGame);
        state.period.addListener((o, a, b) -> syncPicker());
        refresh();
    }

    // ---- period handling ----

    private void pickPeriod() {
        if (updating || monthBox.getValue() == null || yearBox.getValue() == null) return;
        state.period.set(YearMonth.of(yearBox.getValue(), monthBox.getValue()));
        render();
    }

    private void syncPicker() {
        updating = true;
        YearMonth ym = state.period.get();
        ensureYear(ym.getYear());
        monthBox.setValue(ym.getMonth());
        yearBox.setValue(ym.getYear());
        updating = false;
        render();
    }

    /** Years offered: earliest data year (or this year) to next year, by the clock; always includes the selected one. */
    private void ensureYear(int selectedYear) {
        List<Integer> years = PeriodRange.years(finance.summaryByMonth().keySet(), YearMonth.of(selectedYear, 1), clock);
        if (!years.equals(yearBox.getItems())) yearBox.setItems(FXCollections.observableArrayList(years));
    }

    /** Re-reads the data; the first time it picks the current month, or the latest month with data if that is empty. */
    public void refresh() {
        SortedMap<YearMonth, MonthSummary> all = finance.summaryByMonth();
        if (!initialized) {
            initialized = true;
            state.period.set(PeriodRange.defaultMonth(all.keySet(), clock));
        }
        syncPicker();
    }

    // ---- drawing ----

    private void render() {
        YearMonth ym = state.period.get();
        MonthSummary s = finance.summaryFor(ym);
        income.setText(Formats.rupiah(s.income()));
        expense.setText(Formats.rupiah(s.expense()));
        balance.setText(Formats.rupiah(s.balance()));
        balance.getStyleClass().removeAll("income-text", "expense-text");
        if (s.balance() != 0) balance.getStyleClass().add(s.balance() > 0 ? "income-text" : "expense-text");

        renderGame();
        Map<UUID, Long> byCategory = finance.expenseByCategory(ym);
        renderPie(byCategory);
        renderBars(ym);
        renderCategoryCards(byCategory, s.expense());

        List<Transaction> latest = finance.getAll().stream()
                .filter(t -> YearMonth.from(t.getDate()).equals(ym))
                .filter(t -> recent.matches(t, state.search.get()))
                .limit(RECENT_LIMIT).toList();
        recent.setRows(latest);
    }

    /** The month's grade and the current streak. */
    private void renderGame() {
        MonthlyScore score = game.scoreFor(state.period.get());
        gradeChip.getChildren().setAll(new Label(score.grade() == MonthlyScore.Grade.NONE
                ? "Belum ada nilai bulan ini" : "Nilai bulan ini: " + score.grade()));
        gradeChip.getStyleClass().removeAll("meta", "grade-a", "grade-b", "grade-c", "grade-d");
        gradeChip.getStyleClass().add("meta");
        if (score.grade() != MonthlyScore.Grade.NONE) gradeChip.getStyleClass().add("grade-" + score.grade().name().toLowerCase());

        int streak = game.profile().getStreak();
        streakChip.getChildren().setAll(IconCatalog.icon("flame", 16), new Label(streak + " hari"));
        streakChip.getStyleClass().removeAll("meta", "streak");
        streakChip.getStyleClass().add("meta");
        if (streak > 0) streakChip.getStyleClass().add("streak");
    }

    private void renderPie(Map<UUID, Long> byCategory) {
        pie.getData().clear();
        for (Map.Entry<UUID, Long> e : byCategory.entrySet()) {
            Category c = categories.find(e.getKey()).orElse(null);
            String name = c == null ? "?" : c.displayName();
            PieChart.Data slice = new PieChart.Data(name, e.getValue());
            pie.getData().add(slice);
            // pastel slice color + tooltip once the node exists
            slice.nodeProperty().addListener((o, a, node) -> style(node, c, name, e.getValue()));
            if (slice.getNode() != null) style(slice.getNode(), c, name, e.getValue());
        }
    }

    private static void style(javafx.scene.Node node, Category c, String name, long amount) {
        if (node == null) return;
        String color = c == null ? "#9CA3AF" : Tiles.soft(c.getColor(), 0.25);
        node.setStyle("-fx-pie-color: " + color + ";");
        Tooltip.install(node, new Tooltip(name + "\n" + Formats.rupiah(amount)));
    }

    private void renderBars(YearMonth end) {
        XYChart.Series<String, Number> inc = new XYChart.Series<>();
        inc.setName("Pemasukan");
        XYChart.Series<String, Number> exp = new XYChart.Series<>();
        exp.setName("Pengeluaran");
        for (YearMonth m : PeriodRange.lastMonths(end, 6)) {      // crosses New Year without special cases
            MonthSummary s = finance.summaryFor(m);
            String label = m.getMonth().getDisplayName(TextStyle.SHORT, Formats.ID);
            inc.getData().add(new XYChart.Data<>(label, s.income()));
            exp.getData().add(new XYChart.Data<>(label, s.expense()));
        }
        bar.getData().setAll(List.of(inc, exp));
    }

    private void renderCategoryCards(Map<UUID, Long> byCategory, long totalExpense) {
        categoryGrid.getChildren().clear();
        if (byCategory.isEmpty()) {
            Label none = new Label("Belum ada pengeluaran di bulan ini");
            none.getStyleClass().add("muted");
            categoryGrid.getChildren().add(none);
            return;
        }
        for (Map.Entry<UUID, Long> e : byCategory.entrySet()) {
            categories.find(e.getKey()).ifPresent(c -> categoryGrid.getChildren().add(categoryCard(c, e.getValue(), totalExpense)));
        }
    }

    private VBox categoryCard(Category c, long spent, long totalExpense) {
        Label name = new Label(c.getName());
        Label amount = new Label(Formats.rupiah(spent));
        amount.getStyleClass().add("title-2");

        VBox text = new VBox(2, name, amount);
        HBox top = new HBox(12, Tiles.tile(c, 40), text);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, top);
        card.getStyleClass().addAll("card", "compact");
        card.setPrefWidth(240);
        card.setMinWidth(200);

        ProgressBar progress = new ProgressBar();
        progress.setMaxWidth(Double.MAX_VALUE);
        Label caption = new Label();
        caption.getStyleClass().add("caption");
        caption.setWrapText(true);
        caption.setMinHeight(Region.USE_PREF_SIZE); // wrap onto a second line instead of cutting the percentage
        if (c.getMonthlyBudget() != null) {
            double used = (double) spent / c.getMonthlyBudget();
            progress.setProgress(Math.min(used, 1.0));
            if (used > 1.0) progress.getStyleClass().add("over");
            caption.setText(Math.round(used * 100) + "% dari budget " + Formats.rupiah(c.getMonthlyBudget()));
        } else {
            progress.setProgress(totalExpense == 0 ? 0 : (double) spent / totalExpense);
            caption.setText(Math.round(100.0 * spent / Math.max(1, totalExpense)) + "% dari pengeluaran");
        }
        card.getChildren().addAll(progress, caption);
        return card;
    }

    /** The three month totals as one ruled strip: equal cells with a hairline between them. */
    private static HBox statStrip(VBox... cells) {
        HBox strip = new HBox();
        strip.getStyleClass().addAll("card", "stat-strip");
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                Region rule = new Region();
                rule.getStyleClass().add("divider-v");
                strip.getChildren().add(rule);
            }
            HBox.setHgrow(cells[i], Priority.ALWAYS);
            strip.getChildren().add(cells[i]);
        }
        return strip;
    }

    private static VBox statCell(String title, Label value, String valueClass) {
        Label t = new Label(title);
        t.getStyleClass().add("muted");
        value.getStyleClass().add("figure");
        if (valueClass != null) value.getStyleClass().add(valueClass);
        value.setMinWidth(Region.USE_PREF_SIZE); // never truncate an amount
        VBox cell = new VBox(8, t, value);
        cell.setPadding(new Insets(0, 24, 0, 24));
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setPrefWidth(200);
        return cell;
    }

    /** Axis ticks in short Indonesian units: 500 rb, 1,5 jt. */
    private static String compactRupiah(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) return trim(v / 1_000_000) + " jt";
        if (a >= 1_000) return trim(v / 1_000) + " rb";
        return trim(v);
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Formats.ID, "%.1f", d);
    }

    private static VBox card(String title, javafx.scene.Node content) {
        Label t = new Label(title);
        t.getStyleClass().add("title-2");
        VBox card = new VBox(12, t, content);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }
}
