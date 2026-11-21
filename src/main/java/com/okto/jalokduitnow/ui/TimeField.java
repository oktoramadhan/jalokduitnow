package com.okto.jalokduitnow.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A small time picker: an hour list (00-23) and a minute list (every 5 minutes, plus the current value if it differs). */
public class TimeField extends HBox {
    private final ComboBox<Integer> hour = new ComboBox<>(FXCollections.observableArrayList(range(0, 23, 1)));
    private final ComboBox<Integer> minute = new ComboBox<>();
    private Consumer<LocalTime> onChange = t -> { };
    private boolean updating;

    public TimeField() {
        super(6);
        setAlignment(Pos.CENTER_LEFT);
        StringConverter<Integer> two = new StringConverter<>() {
            @Override public String toString(Integer v) { return v == null ? "" : String.format("%02d", v); }
            @Override public Integer fromString(String s) { return null; }
        };
        hour.setConverter(two);
        minute.setConverter(two);
        hour.setPrefWidth(80);
        minute.setPrefWidth(80);
        setMinutes(List.of());
        hour.setOnAction(e -> fire());
        minute.setOnAction(e -> fire());
        getChildren().addAll(hour, new Label(":"), minute);
    }

    /** Shows a time without telling the listener (it is not a user change). */
    public void setTime(LocalTime time) {
        updating = true;
        setMinutes(time.getMinute() % 5 == 0 ? List.of() : List.of(time.getMinute()));
        hour.setValue(time.getHour());
        minute.setValue(time.getMinute());
        updating = false;
    }

    public LocalTime getTime() {
        return LocalTime.of(hour.getValue() == null ? 0 : hour.getValue(), minute.getValue() == null ? 0 : minute.getValue());
    }

    /** Called with the new time whenever the user changes it. */
    public void setOnChange(Consumer<LocalTime> onChange) {
        this.onChange = onChange;
    }

    private void fire() {
        if (!updating && hour.getValue() != null && minute.getValue() != null) onChange.accept(getTime());
    }

    private void setMinutes(List<Integer> extra) {
        List<Integer> minutes = new ArrayList<>(range(0, 55, 5));
        for (int m : extra) if (!minutes.contains(m)) minutes.add(m);
        minutes.sort(Integer::compareTo);
        minute.setItems(FXCollections.observableArrayList(minutes));
    }

    private static List<Integer> range(int from, int to, int step) {
        List<Integer> list = new ArrayList<>();
        for (int i = from; i <= to; i += step) list.add(i);
        return list;
    }
}
