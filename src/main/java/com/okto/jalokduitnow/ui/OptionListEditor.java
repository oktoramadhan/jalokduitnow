package com.okto.jalokduitnow.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The editable options list of a Choice column: add, rename, remove and reorder.
 * It remembers the ORIGINAL name of every row that existed when it was opened, so the caller can tell a rename
 * ("Want" became "Keinginan": values must follow) from a removal (values need a replacement).
 */
public class OptionListEditor extends VBox {
    private static final class Row {
        final String original;          // null for an option added in this session
        final TextField field = new TextField();

        Row(String original, String text) {
            this.original = original;
            field.setText(text);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final VBox rowBox = new VBox(8);

    public OptionListEditor(List<String> initialOptions) {
        setSpacing(10);
        for (String option : initialOptions) rows.add(new Row(option, option));
        Button add = new Button("Tambah opsi", IconCatalog.icon("plus", 14));
        add.setOnAction(e -> {
            Row row = new Row(null, "");
            rows.add(row);
            redraw();
            row.field.requestFocus();
        });
        getChildren().addAll(rowBox, add);
        redraw();
    }

    /** Current options in order, trimmed, blank rows skipped. */
    public List<String> options() {
        List<String> result = new ArrayList<>();
        for (Row r : rows) {
            String text = r.field.getText() == null ? "" : r.field.getText().trim();
            if (!text.isEmpty()) result.add(text);
        }
        return result;
    }

    /** Original name to new name, only for rows that existed at the start and were renamed. */
    public Map<String, String> renames() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Row r : rows) {
            String text = r.field.getText() == null ? "" : r.field.getText().trim();
            if (r.original != null && !text.isEmpty() && !text.equals(r.original)) result.put(r.original, text);
        }
        return result;
    }

    /** Original options that are gone (their row was removed or emptied). */
    public Set<String> removedOriginals() {
        Set<String> kept = new LinkedHashSet<>();
        for (Row r : rows) {
            String text = r.field.getText() == null ? "" : r.field.getText().trim();
            if (r.original != null && !text.isEmpty()) kept.add(r.original);
        }
        Set<String> removed = new LinkedHashSet<>();
        for (Row r : rows) if (r.original != null && !kept.contains(r.original)) removed.add(r.original);
        return removed;
    }

    private void redraw() {
        rowBox.getChildren().clear();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.field.setPromptText("Nama opsi");
            HBox.setHgrow(row.field, Priority.ALWAYS);
            Button up = iconButton("up", "Naikkan", i == 0, () -> move(row, -1));
            Button down = iconButton("down", "Turunkan", i == rows.size() - 1, () -> move(row, 1));
            Button remove = iconButton("delete", "Hapus opsi", false, () -> {
                rows.remove(row);
                redraw();
            });
            HBox box = new HBox(6, row.field, up, down, remove);
            box.setAlignment(Pos.CENTER_LEFT);
            rowBox.getChildren().add(box);
        }
    }

    private void move(Row row, int delta) {
        int index = rows.indexOf(row);
        int target = index + delta;
        if (target < 0 || target >= rows.size()) return;
        rows.remove(index);
        rows.add(target, row);
        redraw();
    }

    private static Button iconButton(String iconKey, String tip, boolean disabled, Runnable action) {
        Button b = new Button();
        b.setGraphic(IconCatalog.icon(iconKey, 16));
        b.getStyleClass().add("ghost");
        b.setTooltip(new Tooltip(tip));
        b.setDisable(disabled);
        b.setOnAction(e -> action.run());
        return b;
    }
}
