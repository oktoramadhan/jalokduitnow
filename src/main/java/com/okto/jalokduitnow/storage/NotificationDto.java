package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.reminder.NotificationAction;

import java.time.Instant;
import java.util.UUID;

/** JSON form of an AppNotification (notifications.json). An unknown action name becomes NONE. */
public record NotificationDto(UUID id, String title, String message, Instant createdAt, boolean read,
                              String action, String reminderId) {

    public static NotificationDto from(AppNotification n) {
        return new NotificationDto(n.getId(), n.getTitle(), n.getMessage(), n.getCreatedAt(), n.isRead(),
                n.getAction().name(), n.getReminderId());
    }

    public AppNotification toNotification() {
        NotificationAction a;
        try {
            a = NotificationAction.valueOf(action);
        } catch (IllegalArgumentException | NullPointerException e) {
            a = NotificationAction.NONE;
        }
        return new AppNotification(id, title, message, createdAt, read, a, reminderId);
    }
}
