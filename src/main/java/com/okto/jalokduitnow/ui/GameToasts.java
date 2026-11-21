package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import javafx.application.Platform;

/**
 * Turns game events into toasts. Events are published on whatever thread caused them,
 * so every handler hops to the FX thread with Platform.runLater before touching the UI.
 */
public final class GameToasts {
    private GameToasts() {}

    public static void install(GameEventBus bus, ToastHost toasts) {
        bus.subscribe(GameEvent.XpGained.class, e -> Platform.runLater(() ->
                toasts.show("+" + e.amount() + " XP", e.reason(), "bolt", "#3B82F6")));
        bus.subscribe(GameEvent.LevelUp.class, e -> Platform.runLater(() ->
                toasts.show("Naik level!", "Level " + e.result().toLevel() + " - " + e.result().title(), "star", "#F59E0B")));
        bus.subscribe(GameEvent.BadgeUnlocked.class, e -> Platform.runLater(() ->
                toasts.show("Badge baru", e.achievement().getName(), e.achievement().getIconKey(), "#8B5CF6")));
        bus.subscribe(GameEvent.QuestCompleted.class, e -> Platform.runLater(() ->
                toasts.show("Quest selesai", e.quest().getTitle(), "target", "#16A34A")));
        bus.subscribe(GameEvent.StreakChanged.class, e -> Platform.runLater(() -> {
            var change = e.change();
            if (change.broken()) toasts.show("Streak terputus", "Mulai lagi hari ini", "flame", "#9CA3AF");
            else if (change.after() > change.before()) toasts.show("Streak " + change.after() + " hari", "Terus catat setiap hari", "flame", "#F97316");
            else if (change.freezeTokensUsed() > 0) toasts.show("Streak diselamatkan", "Freeze token dipakai", "shield", "#3B82F6");
        }));
    }
}
