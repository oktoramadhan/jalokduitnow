package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.reminder.AppNotification;

import java.util.function.Consumer;

/**
 * The clean-up that follows a deletion, for the parts that live outside the core services:
 * <ul>
 *   <li>Notifications that talk about data that is gone are removed, and the matching "already fired" records are forgotten,
 *       so a reminder that no longer applies is not left waiting and a new one can fire for the new data.</li>
 *   <li>After a game reset, this week's quests are started (paying nothing, see GameService.startQuestWeek).</li>
 *   <li>The app's own view state (selection, month, theme after a factory reset) is reset through {@code uiReset}.</li>
 * </ul>
 * After a factory reset the notification and log files are gone, so reloading them is all that is needed.
 */
public class DeletePostActions implements DeleteDataService.PostActions {
    private final NotificationCenter notifications;
    private final ReminderLog reminderLog;
    private final GameService game;
    private final Consumer<DeleteAction> uiReset;

    public DeletePostActions(NotificationCenter notifications, ReminderLog reminderLog, GameService game, Consumer<DeleteAction> uiReset) {
        this.notifications = notifications;
        this.reminderLog = reminderLog;
        this.game = game;
        this.uiReset = uiReset;
    }

    @Override
    public void reloadAuxiliary() throws Exception {
        notifications.reload();
        reminderLog.reload();
    }

    @Override
    public void after(DeleteAction action) {
        switch (action) {
            case TRANSACTIONS -> forget("budget:");                 // spending warnings describe transactions that no longer exist
            case GAME -> {
                forget("streak-risk");                               // there is no streak left to protect
                game.startQuestWeek();
            }
            case FACTORY -> { }                                      // everything was reloaded from empty files
        }
        uiReset.accept(action);
    }

    private void forget(String reminderIdPrefix) {
        notifications.removeIf((AppNotification n) -> n.getReminderId() != null && n.getReminderId().startsWith(reminderIdPrefix));
        reminderLog.forgetPrefix(reminderIdPrefix);
    }
}
