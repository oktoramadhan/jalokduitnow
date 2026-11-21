package com.okto.jalokduitnow.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Light/dark theme. All colors live in theme-light.css / theme-dark.css as variables; this class swaps the
 * palette stylesheet on every scene and dialog that registered, so popups and dialogs follow too.
 * The choice is saved in settings.properties in the data directory.
 */
public final class Theme {
    private static final String BASE = resource("style.css");
    private static final String LIGHT = resource("theme-light.css");
    private static final String DARK = resource("theme-dark.css");

    private static final BooleanProperty DARK_MODE = new SimpleBooleanProperty(false);
    private static final List<WeakReference<ObservableList<String>>> REGISTERED = new ArrayList<>();
    private static Path settingsFile;
    private static boolean loading;

    static {
        DARK_MODE.addListener((o, was, isDark) -> {
            swapPalettes();
            if (!loading) save();
        });
    }

    private Theme() {}

    private static String resource(String name) {
        return Theme.class.getResource(name).toExternalForm();
    }

    public static BooleanProperty darkProperty() {
        return DARK_MODE;
    }

    /** Reads the saved choice (call once at startup, before the scene is shown). */
    public static void load(Path dataDir) {
        settingsFile = dataDir.resolve("settings.properties");
        if (!Files.exists(settingsFile)) return;
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(settingsFile)) {
            p.load(in);
            loading = true;
            DARK_MODE.set("dark".equals(p.getProperty("theme")));
        } catch (IOException e) {
            System.err.println("Pengaturan tema tidak terbaca: " + e.getMessage());
        } finally {
            loading = false;
        }
    }

    private static void save() {
        if (settingsFile == null) return;
        Properties p = new Properties();
        p.setProperty("theme", DARK_MODE.get() ? "dark" : "light");
        try {
            Files.createDirectories(settingsFile.toAbsolutePath().getParent());
            try (Writer out = Files.newBufferedWriter(settingsFile)) {
                p.store(out, "JalokDuitNow settings");
            }
        } catch (IOException e) {
            System.err.println("Pengaturan tema gagal disimpan: " + e.getMessage());
        }
    }

    /** Back to the light theme without writing a settings file (the factory reset removed it). */
    public static void resetToDefault() {
        loading = true;
        try {
            DARK_MODE.set(false);
        } finally {
            loading = false;
        }
    }

    public static void apply(Scene scene) {
        register(scene.getStylesheets());
    }

    public static void styleDialog(DialogPane pane) {
        register(pane.getStylesheets());
    }

    private static void register(ObservableList<String> sheets) {
        sheets.removeAll(LIGHT, DARK, BASE);
        sheets.addAll(palette(), BASE);
        REGISTERED.removeIf(ref -> ref.get() == null);
        REGISTERED.add(new WeakReference<>(sheets));
    }

    private static String palette() {
        return DARK_MODE.get() ? DARK : LIGHT;
    }

    private static void swapPalettes() {
        for (WeakReference<ObservableList<String>> ref : REGISTERED) {
            ObservableList<String> sheets = ref.get();
            if (sheets == null) continue;
            sheets.removeAll(LIGHT, DARK);
            sheets.add(0, palette());
        }
    }
}
