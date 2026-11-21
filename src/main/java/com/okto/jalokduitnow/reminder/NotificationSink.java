package com.okto.jalokduitnow.reminder;

/** A place a fired notification is shown: the in-app toast, the system tray... A failing sink never stops the others. */
@FunctionalInterface
public interface NotificationSink {
    void deliver(AppNotification notification);
}
