package com.okto.jalokduitnow.reminder;

import java.time.Clock;
import java.time.LocalTime;

/** At a chosen time, if no entry was created today yet. Once per day. */
public class DailyLogReminder extends Reminder {
    public static final String ID = "daily-log";
    private final LocalTime at;

    public DailyLogReminder(boolean enabled, LocalTime at) {
        super(ID, "Waktunya mencatat", enabled);
        this.at = at;
    }

    @Override
    public boolean shouldFire(ReminderContext context, Clock clock) {
        return isEnabled() && reached(clock, at) && !context.hasEntryOn(today(clock), clock.getZone());
    }

    @Override
    public String buildMessage(ReminderContext context) {
        return "Belum ada catatan hari ini. Luangkan semenit untuk mencatat transaksimu, ya.";
    }

    @Override public String periodKey(Clock clock) { return today(clock).toString(); }

    @Override public NotificationAction action() { return NotificationAction.ADD_TRANSACTION; }
}
