package com.okto.jalokduitnow.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Entry point. Later stages add the dashboard and transaction views. */
public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        Label hello = new Label("JalokDuitNow - Stage 1 siap");
        stage.setScene(new Scene(new StackPane(hello), 800, 500));
        stage.setTitle("JalokDuitNow");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
