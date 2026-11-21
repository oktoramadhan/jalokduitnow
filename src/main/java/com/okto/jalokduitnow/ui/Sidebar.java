package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.service.GameService;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left pane: app name, level / XP, navigation and the "+ Tambah Transaksi" button.
 * A click on the chevron collapses it to an icon rail and expands it again; it starts expanded.
 */
public class Sidebar extends VBox {
    private static final double EXPANDED_WIDTH = 240;
    private static final double COLLAPSED_WIDTH = 64;
    private static final Duration SLIDE = Duration.millis(160);

    private final ToggleGroup group = new ToggleGroup();
    private final Map<Page, ToggleButton> items = new EnumMap<>(Page.class);
    private final Map<Page, Tooltip> tooltips = new EnumMap<>(Page.class);

    private final DoubleProperty width = new SimpleDoubleProperty(EXPANDED_WIDTH);
    private final StackPane mark = new StackPane(IconCatalog.icon("logo", 20));
    private final Label appName = new Label("JalokDuitNow");
    private final Region brandFill = new Region();
    private final Button toggle = new Button();
    private final HBox brandRow = new HBox();
    private final VBox header = new VBox(8);
    private final VBox levelFull;
    private final Label levelCompact = new Label();
    private final Tooltip levelTip = new Tooltip();
    private final Button add;
    private final Tooltip addTip = new Tooltip("Tambah Transaksi");
    private boolean expanded = true;
    private boolean labelsShown = true;   // follows the animation: false while collapsed or still narrowing/widening
    private Timeline slide;

    public Sidebar(Consumer<Page> onNavigate, Runnable onAdd, GameService game) {
        getStyleClass().add("sidebar");
        setSpacing(2);
        width.addListener((o, a, w) -> {
            setMinWidth(w.doubleValue());
            setPrefWidth(w.doubleValue());
            setMaxWidth(w.doubleValue());
        });
        applyWidth(EXPANDED_WIDTH);

        appName.getStyleClass().add("app-name");
        mark.getStyleClass().add("brand-mark");
        mark.setMinSize(40, 40);
        mark.setPrefSize(40, 40);
        mark.setMaxSize(40, 40);
        HBox.setHgrow(brandFill, Priority.ALWAYS);
        brandRow.setAlignment(Pos.CENTER_LEFT);

        toggle.getStyleClass().add("rail-toggle");
        toggle.setOnAction(e -> setExpanded(!expanded));

        levelFull = levelCard(game);
        levelCompact.getStyleClass().add("level-compact");
        levelCompact.setMaxWidth(Double.MAX_VALUE);
        levelCompact.setTooltip(levelTip);
        levelCompact.setVisible(false);
        levelCompact.setManaged(false);
        refreshLevelCompact(game);
        game.addListener(() -> refreshLevelCompact(game));

        getChildren().addAll(header, spacer(12), levelFull, levelCompact, spacer(12));

        for (Page page : Page.values()) {
            ToggleButton item = new ToggleButton(page.title(), IconCatalog.icon(page.iconKey(), 16));
            item.getStyleClass().add("nav-item");
            item.setToggleGroup(group);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnAction(e -> {
                item.setSelected(true); // a selected item cannot be un-selected by clicking it again
                onNavigate.accept(page);
            });
            items.put(page, item);
            tooltips.put(page, new Tooltip(page.title()));
            getChildren().add(item);
        }

        Region fill = new Region();
        VBox.setVgrow(fill, Priority.ALWAYS);
        add = new Button("Tambah Transaksi", IconCatalog.icon("plus", 16));
        add.getStyleClass().addAll("primary", "add-transaction");
        add.setOnAction(e -> onAdd.run());
        getChildren().addAll(fill, add);

        layoutHeader();
        updateToggleIcon();
    }

    /** Highlights the item of the page that is showing. */
    public void select(Page page) {
        items.get(page).setSelected(true);
    }

    // ---- expand / collapse ----

    private void setExpanded(boolean expand) {
        if (expand == expanded) return;
        expanded = expand;
        if (slide != null) slide.stop();
        if (!expand) showLabels(false);          // text goes first so it is never squeezed while the rail narrows
        slide = new Timeline(new KeyFrame(SLIDE, new KeyValue(width, expand ? EXPANDED_WIDTH : COLLAPSED_WIDTH, Interpolator.EASE_BOTH)));
        if (expand) slide.setOnFinished(e -> showLabels(true));   // text returns once there is room for it
        slide.play();
        updateToggleIcon();
    }

    /** Labels, level card and the wide add button when true; icons only when false. */
    private void showLabels(boolean show) {
        labelsShown = show;
        ContentDisplay display = show ? ContentDisplay.LEFT : ContentDisplay.GRAPHIC_ONLY;
        for (Map.Entry<Page, ToggleButton> e : items.entrySet()) {
            e.getValue().setContentDisplay(display);
            e.getValue().setTooltip(show ? null : tooltips.get(e.getKey()));
        }
        add.setContentDisplay(display);
        add.setTooltip(show ? null : addTip);
        levelFull.setVisible(show);
        levelFull.setManaged(show);
        levelCompact.setVisible(!show);
        levelCompact.setManaged(!show);
        layoutHeader();
    }

    private void layoutHeader() {
        if (labelsShown) {
            brandRow.getChildren().setAll(mark, appName, brandFill, toggle);
            header.getChildren().setAll(brandRow);
        } else {
            brandRow.getChildren().setAll(mark);
            header.getChildren().setAll(brandRow, toggle);
        }
    }

    private void updateToggleIcon() {
        toggle.setGraphic(IconCatalog.icon(expanded ? "left" : "right", 16));
        toggle.setTooltip(new Tooltip(expanded ? "Ciutkan menu" : "Perluas menu"));
        toggle.setAccessibleText(expanded ? "Ciutkan menu" : "Perluas menu");
    }

    private void applyWidth(double w) {
        setMinWidth(w);
        setPrefWidth(w);
        setMaxWidth(w);
    }

    // ---- level ----

    /** Level, title and XP bar, refreshed whenever the game changes. */
    private VBox levelCard(GameService game) {
        Label level = new Label();
        level.getStyleClass().add("level-name");
        Label xp = new Label();
        xp.getStyleClass().add("caption");
        xp.setMinWidth(Region.USE_PREF_SIZE);
        Label title = new Label();
        title.getStyleClass().add("caption");
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox top = new HBox(8, level, grow(), xp);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(8, top, title, bar);
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

    /** The one-line level readout shown in the collapsed rail. */
    private void refreshLevelCompact(GameService game) {
        GameProfile p = game.profile();
        levelCompact.setText("Lv " + p.getLevel());
        levelTip.setText("Level " + p.getLevel() + "\n" + p.getTitle() + "\n" + p.xpIntoLevel() + " / " + p.xpForNextLevel() + " XP");
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
