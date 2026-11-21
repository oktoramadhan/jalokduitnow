package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Transaction;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import com.okto.jalokduitnow.service.PeriodRange;

import java.time.Clock;
import java.time.YearMonth;

/** What the user is looking at, shared by all views: the selected transaction, the month, the search text. */
public final class UiState {
    public final ObjectProperty<Transaction> selected = new SimpleObjectProperty<>();
    public final ObjectProperty<YearMonth> period;
    public final StringProperty search = new SimpleStringProperty("");

    /** The month starts as the current month by the given clock (the dashboard may move it to the latest month with data). */
    public UiState(Clock clock) {
        this.period = new SimpleObjectProperty<>(PeriodRange.current(clock));
    }
}
