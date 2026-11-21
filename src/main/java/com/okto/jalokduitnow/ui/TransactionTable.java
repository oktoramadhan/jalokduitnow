package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.SchemaService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Flat transaction table: category tile + name, date, the visible custom columns, and a green/red amount.
 * Selection is shared through UiState, so the detail panel and every table agree.
 * A compact table (used on the home page) has no custom columns and sizes itself to its rows.
 */
public class TransactionTable extends TableView<Transaction> {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Formats.ID);
    private static final double ROW_HEIGHT = 52;

    private final CategoryService categories;
    private final SchemaService schemaService;
    private final UiState state;
    private final boolean compact;
    private final ObservableList<Transaction> rows = FXCollections.observableArrayList();

    public TransactionTable(CategoryService categories, SchemaService schemaService, UiState state,
                            TransactionActions actions, boolean compact) {
        this.categories = categories;
        this.schemaService = schemaService;
        this.state = state;
        this.compact = compact;

        setItems(rows);
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setFixedCellSize(ROW_HEIGHT);
        setPlaceholder(new Label("Belum ada transaksi"));
        rebuildColumns();

        getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (now != null && now != state.selected.get()) state.selected.set(now);
        });
        state.selected.addListener((o, was, now) -> reselect());
        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) actions.edit(getSelectionModel().getSelectedItem());
        });

        schemaService.addListener(this::rebuildColumns);
        categories.addListener(this::refresh); // renames change what is shown
    }

    public void setRows(List<Transaction> transactions) {
        rows.setAll(transactions);
        if (compact) {
            double h = rows.isEmpty() ? 110 : rows.size() * ROW_HEIGHT + 60;
            setMinHeight(h);
            setPrefHeight(h);
            setMaxHeight(h);
        }
        reselect();
    }

    /** Does the transaction match the search text (category, date, amount or a custom value)? */
    public boolean matches(Transaction t, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        StringBuilder haystack = new StringBuilder();
        haystack.append(categories.nameOf(t.getCategoryId())).append(' ')
                .append(t.getDate().format(DATE)).append(' ').append(t.getDate()).append(' ')
                .append(t.getAmount()).append(' ').append(Formats.rupiah(t.getAmount())).append(' ')
                .append(t.typeLabel());
        for (ColumnDefinition col : schemaService.getSchema().visibleColumns()) {
            haystack.append(' ').append(col.getType().displayValue(t.getCustomValue(col.getId())));
        }
        return haystack.toString().toLowerCase(Locale.ROOT).contains(q);
    }

    private void reselect() {
        Transaction wanted = state.selected.get();
        if (wanted != null && rows.contains(wanted)) {
            if (getSelectionModel().getSelectedItem() != wanted) getSelectionModel().select(wanted);
        } else if (getSelectionModel().getSelectedItem() != null && wanted == null) {
            getSelectionModel().clearSelection();
        }
    }

    private void rebuildColumns() {
        List<TableColumn<Transaction, ?>> columns = new ArrayList<>();
        columns.add(categoryColumn());
        columns.add(textColumn("TANGGAL", 120, t -> t.getDate().format(DATE)));
        if (!compact) {
            for (ColumnDefinition col : schemaService.getSchema().visibleColumns()) {
                columns.add(textColumn(col.getName().toUpperCase(Formats.ID), 150,
                        t -> col.getType().displayValue(t.getCustomValue(col.getId()))));
            }
        }
        columns.add(amountColumn());
        getColumns().setAll(columns);
        refresh();
    }

    private TableColumn<Transaction, Transaction> categoryColumn() {
        TableColumn<Transaction, Transaction> col = new TableColumn<>("KATEGORI");
        col.setPrefWidth(240);
        col.setSortable(false);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setGraphic(null);
                    return;
                }
                Category category = categories.find(t.getCategoryId()).orElse(null);
                HBox box = new HBox(12);
                box.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(category == null ? "?" : category.getName());
                name.setStyle("-fx-font-weight: bold;");
                box.getChildren().addAll(category == null ? new Label("•") : Tiles.tile(category, 36), name);
                setGraphic(box);
                setText(null);
            }
        });
        return col;
    }

    private TableColumn<Transaction, String> textColumn(String title, double width,
                                                        java.util.function.Function<Transaction, String> value) {
        TableColumn<Transaction, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setSortable(false);
        col.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                getStyleClass().remove("muted");
                if (!empty) getStyleClass().add("muted");
            }
        });
        return col;
    }

    private TableColumn<Transaction, Transaction> amountColumn() {
        TableColumn<Transaction, Transaction> col = new TableColumn<>("JUMLAH");
        col.setPrefWidth(150);
        col.setSortable(false);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                getStyleClass().removeAll("amount", "income", "expense");
                if (empty || t == null) {
                    setText(null);
                    return;
                }
                setText(Formats.signedRupiah(t.signedAmount()));
                getStyleClass().addAll("amount", t.signedAmount() > 0 ? "income" : "expense");
            }
        });
        return col;
    }
}
