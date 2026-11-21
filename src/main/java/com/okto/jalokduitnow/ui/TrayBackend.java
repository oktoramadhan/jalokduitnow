package com.okto.jalokduitnow.ui;

/** What the tray notifier needs from the operating system. A seam, so tests never touch a real tray. */
public interface TrayBackend {
    boolean isSupported();

    void show(String title, String message);

    /** Removes the tray icon, if one was added. Safe to call when nothing was shown. */
    void close();
}
