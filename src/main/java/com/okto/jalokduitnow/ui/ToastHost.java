package com.okto.jalokduitnow.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Non-blocking notifications in the bottom-right corner. A toast fades in, stays a moment and fades out;
 * it never takes focus and ignores the mouse, so it cannot get in the way. Call from the FX thread.
 */
public class ToastHost extends VBox {
    private static final int MAX_VISIBLE = 4;
    private static final Duration FADE_IN = Duration.millis(180);
    private static final Duration STAY = Duration.millis(2600);
    private static final Duration FADE_OUT = Duration.millis(320);

    public ToastHost() {
        setAlignment(Pos.BOTTOM_RIGHT);
        setSpacing(10);
        setPadding(new Insets(24));
        setMouseTransparent(true);   // clicks pass through to the app underneath
        setPickOnBounds(false);
    }

    /** @param iconKey key in IconCatalog; @param color tile color "#rrggbb" */
    public void show(String title, String message, String iconKey, String color) {
        Label t = new Label(title);
        t.getStyleClass().add("toast-title");
        VBox text = new VBox(2, t);
        if (message != null && !message.isBlank()) {
            Label m = new Label(message);
            m.getStyleClass().add("muted");
            text.getChildren().add(m);
        }
        HBox toast = new HBox(12, Tiles.tile(iconKey, color, 36), text);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().add("toast");
        toast.setMaxWidth(340);
        toast.setOpacity(0);

        if (getChildren().size() >= MAX_VISIBLE) getChildren().remove(0); // drop the oldest
        getChildren().add(toast);

        FadeTransition in = new FadeTransition(FADE_IN, toast);
        in.setToValue(1);
        TranslateTransition slide = new TranslateTransition(FADE_IN, toast);
        slide.setFromY(12);
        slide.setToY(0);
        FadeTransition out = new FadeTransition(FADE_OUT, toast);
        out.setToValue(0);
        SequentialTransition run = new SequentialTransition(new ParallelTransition(in, slide), new PauseTransition(STAY), out);
        run.setOnFinished(e -> getChildren().remove(toast));
        run.play();
    }
}
