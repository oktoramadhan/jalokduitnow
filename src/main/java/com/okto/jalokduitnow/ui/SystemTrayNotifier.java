package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.reminder.NotificationSink;

import java.awt.AWTError;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/**
 * Shows a notification in the system tray when the platform supports it and the user allowed it.
 * It must never take the app down: an unsupported or headless system is silently skipped, and any failure
 * (an exception from AWT, a missing native part) switches the tray off for the rest of the session, so the
 * in-app toast and notification center are all that remain. The work runs on its own daemon thread, not the FX thread.
 */
public class SystemTrayNotifier implements NotificationSink {
    private final TrayBackend backend;
    private final BooleanSupplier enabled;
    private final Executor executor;
    private volatile boolean unavailable;

    public SystemTrayNotifier(TrayBackend backend, BooleanSupplier enabled) {
        this(backend, enabled, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "tray-notifier");
            t.setDaemon(true);
            return t;
        }));
    }

    public SystemTrayNotifier(TrayBackend backend, BooleanSupplier enabled, Executor executor) {
        this.backend = backend;
        this.enabled = enabled;
        this.executor = executor;
    }

    /** True once the tray has been found unsupported or has failed. */
    public boolean isUnavailable() {
        return unavailable;
    }

    @Override
    public void deliver(AppNotification notification) {
        if (unavailable || !enabled.getAsBoolean()) return;
        try {
            executor.execute(() -> show(notification));
        } catch (RuntimeException e) {
            unavailable = true;
        }
    }

    /**
     * Removes the tray icon. Call when the app exits: an icon left in the tray keeps the AWT threads alive, and the
     * process would not end after the window is closed.
     */
    public void close() {
        try {
            executor.execute(backend::close);
        } catch (RuntimeException e) {
            // the executor is already gone: nothing was shown through it anyway
        }
    }

    private void show(AppNotification n) {
        try {
            if (!backend.isSupported()) {
                unavailable = true;
                return;
            }
            backend.show(n.getTitle(), n.getMessage());
        } catch (Exception | LinkageError | AWTError e) {
            unavailable = true;   // never crash: fall back to in-app notifications only
        }
    }
}
