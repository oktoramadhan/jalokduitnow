package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.SchemaService;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** The Transaksi page: every transaction, newest first, filtered by the search box. */
public class TransactionView extends VBox {
    private final FinanceService finance;
    private final UiState state;
    private final TransactionTable table;
    private final Label count = new Label();

    public TransactionView(FinanceService finance, SchemaService schemaService, CategoryService categories,
                           UiState state, TransactionActions actions) {
        this.finance = finance;
        this.state = state;
        this.table = new TransactionTable(categories, schemaService, state, actions, false);

        Label title = new Label("Semua Transaksi");
        title.getStyleClass().add("title-2");
        count.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, title, spacer, count);
        header.setAlignment(Pos.CENTER_LEFT);

        setSpacing(16);
        getChildren().addAll(header, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        finance.addListener(this::refresh);
        state.search.addListener((o, a, b) -> refresh());
        refresh();
    }

    private void refresh() {
        List<Transaction> shown = finance.getAll().stream().filter(t -> table.matches(t, state.search.get())).toList();
        table.setRows(shown);
        count.setText(shown.size() + " transaksi");
    }
}
