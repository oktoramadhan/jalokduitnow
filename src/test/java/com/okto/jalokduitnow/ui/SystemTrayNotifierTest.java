package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.reminder.NotificationAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** The tray must never crash the app: unsupported, disabled and failing trays are all handled. */
class SystemTrayNotifierTest {
    private static AppNotification note(String title) {
        return AppNotification.create(title, "isi pesan", Instant.parse("2026-11-21T10:00:00Z"), NotificationAction.NONE, "x");
    }

    /** A fake tray that records what it was asked to show. */
    static class FakeTray implements TrayBackend {
        boolean supported = true;
        RuntimeException failWith;
        Error failWithError;
        final List<String> shown = new ArrayList<>();
        int closed;

        @Override public boolean isSupported() { return supported; }

        @Override public void close() { closed++; }

        @Override public void show(String title, String message) {
            if (failWith != null) throw failWith;
            if (failWithError != null) throw failWithError;
            shown.add(title + "|" + message);
        }
    }

    @Test
    void aSupportedTrayShowsTheTitleAndMessage() {
        FakeTray tray = new FakeTray();
        new SystemTrayNotifier(tray, () -> true, Runnable::run).deliver(note("Waktunya mencatat"));
        assertEquals(List.of("Waktunya mencatat|isi pesan"), tray.shown);
    }

    @Test
    void anUnsupportedTrayIsSkippedQuietlyAndRemembered() {
        FakeTray tray = new FakeTray();
        tray.supported = false;
        SystemTrayNotifier notifier = new SystemTrayNotifier(tray, () -> true, Runnable::run);
        assertDoesNotThrow(() -> notifier.deliver(note("a")));
        assertTrue(tray.shown.isEmpty());
        assertTrue(notifier.isUnavailable());
    }

    @Test
    void whenTheUserSwitchedTheTrayOffNothingIsShown() {
        FakeTray tray = new FakeTray();
        AtomicBoolean on = new AtomicBoolean(false);
        SystemTrayNotifier notifier = new SystemTrayNotifier(tray, on::get, Runnable::run);
        notifier.deliver(note("a"));
        assertTrue(tray.shown.isEmpty());
        on.set(true);                                     // switching it on again works: it was not a failure
        notifier.deliver(note("b"));
        assertEquals(1, tray.shown.size());
        assertFalse(notifier.isUnavailable());
    }

    @Test
    void aFailingTrayNeverThrowsAndIsNotTriedAgain() {
        FakeTray tray = new FakeTray();
        tray.failWith = new IllegalStateException("no tray icon");
        SystemTrayNotifier notifier = new SystemTrayNotifier(tray, () -> true, Runnable::run);
        assertDoesNotThrow(() -> notifier.deliver(note("a")));
        assertTrue(notifier.isUnavailable());

        tray.failWith = null;
        notifier.deliver(note("b"));
        assertTrue(tray.shown.isEmpty(), "once it has failed the tray stays off for the session");
    }

    @Test
    void awtErrorsAndMissingNativePartsAreAlsoContained() {
        FakeTray tray = new FakeTray();
        tray.failWithError = new java.awt.AWTError("no toolkit");
        SystemTrayNotifier first = new SystemTrayNotifier(tray, () -> true, Runnable::run);
        assertDoesNotThrow(() -> first.deliver(note("a")));
        assertTrue(first.isUnavailable());

        FakeTray other = new FakeTray();
        other.failWithError = new UnsatisfiedLinkError("native library");
        SystemTrayNotifier second = new SystemTrayNotifier(other, () -> true, Runnable::run);
        assertDoesNotThrow(() -> second.deliver(note("a")));
        assertTrue(second.isUnavailable());
    }

    @Test
    void aRejectedBackgroundTaskIsContainedToo() {
        SystemTrayNotifier notifier = new SystemTrayNotifier(new FakeTray(), () -> true, task -> {
            throw new java.util.concurrent.RejectedExecutionException("shut down");
        });
        assertDoesNotThrow(() -> notifier.deliver(note("a")));
        assertTrue(notifier.isUnavailable());
    }

    @Test
    void theRealBackendCanAlwaysBeAskedWhetherItIsSupported() {
        // headless CI, a server without a display or a desktop: either answer is fine, a crash is not
        assertDoesNotThrow(() -> new AwtTrayBackend().isSupported());
    }

    @Test
    void closingRemovesTheTrayIconEvenAfterAFailure() {
        FakeTray tray = new FakeTray();
        SystemTrayNotifier notifier = new SystemTrayNotifier(tray, () -> true, Runnable::run);
        notifier.deliver(note("a"));
        notifier.close();
        assertEquals(1, tray.closed);

        FakeTray failing = new FakeTray();
        failing.failWith = new IllegalStateException("x");
        SystemTrayNotifier broken = new SystemTrayNotifier(failing, () -> true, Runnable::run);
        broken.deliver(note("a"));
        assertDoesNotThrow(broken::close);
        assertEquals(1, failing.closed, "the icon is removed even if showing failed");
    }

    @Test
    void closingWhenTheExecutorIsGoneIsHarmless() {
        SystemTrayNotifier notifier = new SystemTrayNotifier(new FakeTray(), () -> true, task -> {
            throw new java.util.concurrent.RejectedExecutionException("shut down");
        });
        assertDoesNotThrow(notifier::close);
    }
}
