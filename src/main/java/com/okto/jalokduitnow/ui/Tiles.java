package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Category;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;

/** Icon tiles: a colored rounded square with a white vector icon on top. */
public final class Tiles {
    private Tiles() {}

    /** "rgba(r,g,b,a)" for a "#rrggbb" color. */
    public static String rgba(String hex, double alpha) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return "rgba(" + r + "," + g + "," + b + "," + alpha + ")";
    }

    /** The color mixed toward white: a soft pastel version for charts. */
    public static String soft(String hex, double mixWithWhite) {
        int r = mix(Integer.parseInt(hex.substring(1, 3), 16), mixWithWhite);
        int g = mix(Integer.parseInt(hex.substring(3, 5), 16), mixWithWhite);
        int b = mix(Integer.parseInt(hex.substring(5, 7), 16), mixWithWhite);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static int mix(int channel, double amount) {
        return (int) Math.round(channel + (255 - channel) * amount);
    }

    public static StackPane tile(String iconKey, String hexColor, double size) {
        StackPane tile = new StackPane(IconCatalog.whiteIcon(iconKey, (int) Math.round(size * 0.5)));
        tile.setAlignment(Pos.CENTER);
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);
        tile.setStyle("-fx-background-color: " + hexColor + "; -fx-background-radius: " + Math.round(size * 0.32) + ";");
        return tile;
    }

    public static StackPane tile(Category category, double size) {
        return tile(category.getIconKey(), category.getColor(), size);
    }

    /** A greyed tile (locked badge). */
    public static StackPane lockedTile(String iconKey, double size) {
        StackPane tile = new StackPane(IconCatalog.icon(iconKey, (int) Math.round(size * 0.5)));
        tile.setAlignment(Pos.CENTER);
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);
        tile.getStyleClass().add("tile-locked");
        tile.setStyle("-fx-background-radius: " + Math.round(size * 0.32) + ";");
        return tile;
    }
}
