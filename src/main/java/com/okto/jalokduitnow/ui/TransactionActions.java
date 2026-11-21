package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.SchemaService;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.time.Clock;
import java.time.YearMonth;

/** The add / edit / delete flows (dialogs), shared by the sidebar button, the tables and the detail panel. */
public final class TransactionActions {
    private final FinanceService finance;
    private final SchemaService schema;
    private final CategoryService categories;
    private final UiState state;
    private final Clock clock;

    public TransactionActions(FinanceService finance, SchemaService schema, CategoryService categories,
                              UiState state, Clock clock) {
        this.finance = finance;
        this.schema = schema;
        this.categories = categories;
        this.state = state;
        this.clock = clock;
        // an edit replaces the Transaction object (same id): keep the selection pointing at the live one
        finance.addListener(this::resync);
    }

    public void add() {
        TransactionDialog.show(categories, schema.getSchema(), null, clock).ifPresent(t -> {
            finance.add(t);
            state.period.set(YearMonth.from(t.getDate())); // show the month the new entry belongs to
            state.selected.set(t);
        });
    }

    public void edit(Transaction transaction) {
        if (transaction == null) return;
        TransactionDialog.show(categories, schema.getSchema(), transaction, clock).ifPresent(t -> {
            finance.update(t);
            state.selected.set(t);
        });
    }

    public void delete(Transaction transaction) {
        if (transaction == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Hapus "
                + categories.nameOf(transaction.getCategoryId()) + " " + Formats.rupiah(transaction.getAmount()) + "?");
        Theme.styleDialog(confirm.getDialogPane());
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            finance.delete(transaction.getId());
            state.selected.set(null);
        });
    }

    private void resync() {
        Transaction current = state.selected.get();
        if (current == null) return;
        state.selected.set(finance.getAll().stream().filter(t -> t.getId().equals(current.getId())).findFirst().orElse(null));
    }
}
