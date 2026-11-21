package com.okto.jalokduitnow;

import com.okto.jalokduitnow.ui.MainApp;
import javafx.application.Application;

/**
 * Entry point for the packaged app (jpackage). It does not extend Application on purpose: a main class that extends
 * Application makes the JVM insist on JavaFX being a named module, which fails when JavaFX is on the classpath.
 */
public final class Launcher {
    private Launcher() { }

    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
