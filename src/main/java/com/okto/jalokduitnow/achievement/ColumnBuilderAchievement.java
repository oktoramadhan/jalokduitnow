package com.okto.jalokduitnow.achievement;

import com.okto.jalokduitnow.service.SchemaService;

/**
 * Build your own table: have at least {@code minColumns} columns you created yourself.
 * The free-text column every schema starts with does not count.
 */
public class ColumnBuilderAchievement extends Achievement {
    private final int minColumns;

    public ColumnBuilderAchievement(int minColumns) {
        super("column-builder", "Perancang Kolom",
                minColumns == 1 ? "Buat kolom kustom pertamamu" : "Buat minimal " + minColumns + " kolom kustom", "columns");
        assert minColumns >= 1;
        this.minColumns = minColumns;
    }

    @Override
    public boolean check(GameContext c) {
        long built = c.schema().getColumns().stream()
                .filter(col -> !col.getId().equals(SchemaService.DEFAULT_COLUMN_ID)).count();
        return built >= minColumns;
    }
}
