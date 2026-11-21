package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.FinanceService.MonthSummary;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Map;
import java.util.SortedMap;

/** Month picker, Income/Expense/Balance cards, expense PieChart and a monthly BarChart. */
public class DashboardView extends VBox {
    private final FinanceService service;
    private final ComboBox<Month> monthBox = new ComboBox<>(FXCollections.observableArrayList(Month.values()));
    private final ComboBox<Integer> yearBox = new ComboBox<>();
    private final Label income = card(), expense = card(), balance = card();
    private final PieChart pie = new PieChart();
    private final BarChart<String, Number> bar = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private boolean updating; // suppress combo events while we set them programmatically

    public DashboardView(FinanceService service) {
        this.service = service;
        setSpacing(12);
        setPadding(new Insets(15));

        monthBox.setConverter(new StringConverter<>() {
            @Override public String toString(Month m) { return m == null ? "" : m.getDisplayName(TextStyle.FULL, Formats.ID); }
            @Override public Month fromString(String s) { return null; }
        });
        monthBox.setOnAction(e -> { if (!updating) render(); });
        yearBox.setOnAction(e -> { if (!updating) render(); });

        HBox picker = new HBox(8, new Label("Periode:"), monthBox, yearBox);
        picker.setAlignment(Pos.CENTER_LEFT);
        HBox cards = new HBox(12, box("Income", income), box("Expense", expense), box("Balance", balance));

        pie.setTitle("Pengeluaran per Kategori");
        pie.setLegendVisible(false);
        bar.setTitle("Income vs Expense per Bulan");
        bar.setAnimated(false);
        HBox charts = new HBox(12, pie, bar);
        HBox.setHgrow(pie, Priority.ALWAYS);
        HBox.setHgrow(bar, Priority.ALWAYS);
        VBox.setVgrow(charts, Priority.ALWAYS);

        getChildren().addAll(picker, cards, charts);

        service.addListener(this::refresh);
        refresh();
    }

    /** Reloads year choices from the data, keeps the selected period if still valid. */
    public void refresh() {
        SortedMap<YearMonth, MonthSummary> all = service.summaryByMonth();
        updating = true;
        Month m = monthBox.getValue();
        Integer y = yearBox.getValue();
        var years = all.keySet().stream().map(YearMonth::getYear).distinct().toList();
        yearBox.setItems(FXCollections.observableArrayList(years.isEmpty() ? java.util.List.of(YearMonth.now().getYear()) : years));
        if (m == null || y == null || !yearBox.getItems().contains(y)) {
            YearMonth start = all.isEmpty() ? YearMonth.now() : all.lastKey(); // default: latest month with data
            m = start.getMonth();
            y = start.getYear();
        }
        monthBox.setValue(m);
        yearBox.setValue(y);
        updating = false;
        render();
    }

    private void render() {
        if (monthBox.getValue() == null || yearBox.getValue() == null) return;
        YearMonth ym = YearMonth.of(yearBox.getValue(), monthBox.getValue());
        MonthSummary s = service.summaryFor(ym);
        income.setText(Formats.rupiah(s.income()));
        expense.setText(Formats.rupiah(s.expense()));
        balance.setText(Formats.rupiah(s.balance()));

        pie.getData().clear();
        for (Map.Entry<String, Long> e : service.expenseByCategory(ym).entrySet()) {
            pie.getData().add(new PieChart.Data(e.getKey() + " (" + Formats.rupiah(e.getValue()) + ")", e.getValue()));
        }

        XYChart.Series<String, Number> inc = new XYChart.Series<>();
        inc.setName("Income");
        XYChart.Series<String, Number> exp = new XYChart.Series<>();
        exp.setName("Expense");
        service.summaryByMonth().forEach((month, sum) -> {
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Formats.ID) + " " + (month.getYear() % 100);
            inc.getData().add(new XYChart.Data<>(label, sum.income()));
            exp.getData().add(new XYChart.Data<>(label, sum.expense()));
        });
        bar.getData().setAll(java.util.List.of(inc, exp));
    }

    private static Label card() {
        Label l = new Label("-");
        l.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        return l;
    }

    private static VBox box(String title, Label value) {
        VBox b = new VBox(4, new Label(title), value);
        b.setPadding(new Insets(10, 16, 10, 16));
        b.setStyle("-fx-background-color: -fx-box-border, -fx-background; -fx-background-insets: 0, 1; -fx-background-radius: 6;");
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }
}
