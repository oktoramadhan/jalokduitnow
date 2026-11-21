package com.okto.jalokduitnow.achievement;

/** Build your own table: have at least {@code minColumns} custom columns. */
public class ColumnBuilderAchievement extends Achievement {
    private final int minColumns;

    public ColumnBuilderAchievement(int minColumns) {
        super("column-builder", "Perancang Kolom", "Punya minimal " + minColumns + " kolom kustom", "columns");
        this.minColumns = minColumns;
    }

    @Override
    public boolean check(GameContext c) {
        return c.schema().getColumns().size() >= minColumns;
    }
}
