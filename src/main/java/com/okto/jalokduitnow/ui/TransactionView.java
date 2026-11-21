package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.service.FinanceService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.time.format.DateTimeFormatter;

/** Table of all transactions with add / edit / delete. */
public class TransactionView extends BorderPane {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Formats.ID);

    private final FinanceService service;
    private final ObservableList<Transaction> items = FXCollections.observableArrayList();
    private final TableView<Transaction> table = new TableView<>(items);

    public TransactionView(FinanceService service) {
        this.service = service;
        buildTable();

        Button add = new Button("Tambah");
        Button edit = new Button("Ubah");
        Button delete = new Button("Hapus");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        add.setOnAction(e -> onAdd());
        edit.setOnAction(e -> onEdit());
        delete.setOnAction(e -> onDelete());
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !edit.isDisabled()) onEdit(); });

        HBox bar = new HBox(8, add, edit, delete);
        bar.setPadding(new Insets(0, 0, 10, 0));
        setPadding(new Insets(10));
        setTop(bar);
        setCenter(table);

        service.addListener(this::refresh);
        refresh();
    }

    private void buildTable() {
        table.getColumns().add(column("Tanggal", 110, t -> t.getDate().format(DATE)));
        table.getColumns().add(column("Tipe", 80, Transaction::typeLabel));
        table.getColumns().add(column("Kategori", 140, t -> t.getCategory().name()));
        TableColumn<Transaction, String> amount = column("Jumlah", 140, t -> Formats.rupiah(t.getAmount()));
        amount.setStyle("-fx-alignment: CENTER-RIGHT;");
        table.getColumns().add(amount);
        TableColumn<Transaction, String> desc = column("Keterangan", 300, Transaction::getDescription);
        table.getColumns().add(desc);
        table.setPlaceholder(new Label("Belum ada transaksi. Tambah atau import dari Excel."));
    }

    private TableColumn<Transaction, String> column(String title, double width,
                                                    java.util.function.Function<Transaction, String> value) {
        TableColumn<Transaction, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        col.setSortable(false); // service keeps newest-first order
        return col;
    }

    public void refresh() {
        items.setAll(service.getAll());
    }

    private void onAdd() {
        TransactionDialog.show(service, null).ifPresent(t -> run(() -> service.add(t)));
    }

    private void onEdit() {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        TransactionDialog.show(service, selected).ifPresent(t -> run(() -> service.update(t)));
    }

    private void onDelete() {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Hapus " + selected.getCategory().name() + " " + Formats.rupiah(selected.getAmount()) + "?");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> run(() -> service.delete(selected.getId())));
    }

    @FunctionalInterface
    private interface StorageAction { void run() throws StorageException; }

    private void run(StorageAction action) {
        try {
            action.run();
        } catch (StorageException e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }
}
