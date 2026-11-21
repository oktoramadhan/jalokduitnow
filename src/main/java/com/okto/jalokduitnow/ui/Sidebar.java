package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.service.GameService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Left pane: app name, level / XP, navigation and the "+ Tambah Transaksi" button. */
public class Sidebar extends VBox {
    private final ToggleGroup group = new ToggleGroup();
    private final Map<Page, ToggleButton> items = new EnumMap<>(Page.class);

    public Sidebar(Consumer<Page> onNavigate, Runnable onAdd, GameService game) {
        getStyleClass().add("sidebar");
        setSpacing(8);
        setMinWidth(244);
        setPrefWidth(244);
        setMaxWidth(244);

        Label appName = new Label("JalokDuitNow");
        appName.getStyleClass().add("app-name");
        HBox brand = new HBox(12, Tiles.tile("logo", "#3B82F6", 36), appName);
        brand.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(brand, spacer(12), levelCard(game), spacer(12));

        for (Page page : Page.values()) {
            ToggleButton item = new ToggleButton(page.title(), IconCatalog.icon(page.iconKey(), 18));
            item.getStyleClass().add("nav-item");
            item.setToggleGroup(group);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnAction(e -> {
                item.setSelected(true); // a selected item cannot be un-selected by clicking it again
                onNavigate.accept(page);
            });
            Motion.nudge(item);
            items.put(page, item);
            getChildren().add(item);
        }

        Region fill = new Region();
        VBox.setVgrow(fill, Priority.ALWAYS);
        Button add = new Button("Tambah Transaksi", IconCatalog.icon("plus", 18));
        add.getStyleClass().addAll("primary", "add-transaction");
        add.setOnAction(e -> onAdd.run());
        Motion.grow(add);
        getChildren().addAll(fill, add);
    }

    /** Highlights the item of the page that is showing. */
    public void select(Page page) {
        items.get(page).setSelected(true);
    }

    /** Level, title and XP bar, refreshed whenever the game changes. */
    private VBox levelCard(GameService game) {
        Label level = new Label();
        level.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label xp = new Label();
        xp.getStyleClass().add("caption");
        xp.setStyle("-fx-text-fill: -accent-text;");
        Label title = new Label();
        title.getStyleClass().add("caption");
        title.setStyle("-fx-text-fill: -accent-text;");
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox top = new HBox(8, level, grow(), xp);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(6, top, title, bar);
        card.getStyleClass().add("level-card");

        Runnable refresh = () -> {
            GameProfile p = game.profile();
            level.setText("Level " + p.getLevel());
            title.setText(p.getTitle());
            xp.setText(p.xpIntoLevel() + " / " + p.xpForNextLevel() + " XP");
            bar.setProgress(p.levelProgress());
        };
        game.addListener(refresh);
        refresh.run();
        return card;
    }

    private static Region grow() {
        Region r = new Region();
        javafx.scene.layout.HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static Region spacer(double height) {
        Region r = new Region();
        r.setMinHeight(height);
        r.setPrefHeight(height);
        return r;
    }
}
