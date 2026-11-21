package com.okto.jalokduitnow.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.Property;
import javafx.scene.Node;
import javafx.util.Duration;

/** Soft hover transitions (120 ms). JavaFX CSS has no transitions, so they are done here. */
public final class Motion {
    private static final Duration DURATION = Duration.millis(120);

    private Motion() {}

    /** Cards: rise 2px while hovered. */
    public static void lift(Node node) {
        hover(node, node.translateYProperty(), 0, -2, "motion.lift");
    }

    /** Buttons: grow slightly while hovered. */
    public static void grow(Node node) {
        hover(node, node.scaleXProperty(), 1.0, 1.03, "motion.growX");
        hover(node, node.scaleYProperty(), 1.0, 1.03, "motion.growY");
    }

    /** Navigation items: slide 3px to the right while hovered. */
    public static void nudge(Node node) {
        hover(node, node.translateXProperty(), 0, 3, "motion.nudge");
    }

    private static void hover(Node node, Property<Number> property, double rest, double hovered, String key) {
        node.setOnMouseEntered(e -> play(node, property, hovered, key));
        node.setOnMouseExited(e -> play(node, property, rest, key));
    }

    private static void play(Node node, Property<Number> property, double target, String key) {
        if (node.getProperties().get(key) instanceof Timeline running) running.stop();
        Timeline t = new Timeline(new KeyFrame(DURATION, new KeyValue(property, target, Interpolator.EASE_BOTH)));
        node.getProperties().put(key, t);
        t.play();
    }
}
