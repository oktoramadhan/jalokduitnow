package com.okto.jalokduitnow.reminder;

import java.time.Instant;
import java.util.UUID;

/** One entry in the notification center. Only the read flag ever changes. */
public class AppNotification {
    private final UUID id;
    private final String title;
    private final String message;
    private final Instant createdAt;
    private final NotificationAction action;
    private final String reminderId;     // null for a test notification
    private boolean read;

    public AppNotification(UUID id, String title, String message, Instant createdAt, boolean read,
                           NotificationAction action, String reminderId) {
        assert id != null && title != null && message != null && createdAt != null && action != null;
        this.id = id;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
        this.read = read;
        this.action = action;
        this.reminderId = reminderId;
    }

    public static AppNotification create(String title, String message, Instant at, NotificationAction action, String reminderId) {
        return new AppNotification(UUID.randomUUID(), title, message, at, false, action, reminderId);
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public NotificationAction getAction() { return action; }
    public String getReminderId() { return reminderId; }
    public boolean isRead() { return read; }

    /** Public only because NotificationCenter (another package) calls it; nothing else should. */
    public void markRead() { this.read = true; }
}
