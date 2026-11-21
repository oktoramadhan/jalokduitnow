package com.okto.jalokduitnow.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** A page that is not built yet: a friendly card instead of an empty screen. */
public class PlaceholderView extends VBox {
    public PlaceholderView(String iconKey, String title, String text) {
        StackPane icon = Tiles.tile(iconKey, "#3B82F6", 64);
        Label heading = new Label(title);
        heading.getStyleClass().add("title-2");
        Label body = new Label(text);
        body.getStyleClass().add("muted");
        body.setWrapText(true);
        body.setMaxWidth(380);
        body.setAlignment(Pos.CENTER);
        body.setStyle("-fx-text-alignment: center;");

        VBox card = new VBox(10, icon, heading, body);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(520);
        card.setPrefHeight(260);

        setAlignment(Pos.TOP_CENTER);
        getChildren().add(card);
    }
}
