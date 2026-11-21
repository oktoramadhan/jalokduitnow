package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Transaction;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.YearMonth;

/** What the user is looking at, shared by all views: the selected transaction, the month, the search text. */
public final class UiState {
    public final ObjectProperty<Transaction> selected = new SimpleObjectProperty<>();
    public final ObjectProperty<YearMonth> period = new SimpleObjectProperty<>(YearMonth.now());
    public final StringProperty search = new SimpleStringProperty("");
}
