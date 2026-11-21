package com.okto.jalokduitnow.reminder;

import java.time.Clock;
import java.time.LocalTime;

/**
 * In the evening, if a streak is running and nothing was recorded today. If a freeze token is available the message
 * says so, so the user knows the streak has a safety net. Once per day.
 */
public class StreakRiskReminder extends Reminder {
    public static final String ID = "streak-risk";
    private final LocalTime at;

    public StreakRiskReminder(boolean enabled, LocalTime at) {
        super(ID, "Jaga streak-mu", enabled);
        this.at = at;
    }

    @Override
    public boolean shouldFire(ReminderContext context, Clock clock) {
        return isEnabled() && reached(clock, at) && context.streak() > 0
                && !context.hasEntryOn(today(clock), clock.getZone());
    }

    @Override
    public String buildMessage(ReminderContext context) {
        String text = "Streak " + context.streak() + " hari sedang berjalan. Satu catatan hari ini akan melanjutkannya.";
        if (context.freezeTokens() > 0) {
            text += " Kamu juga punya " + context.freezeTokens() + " freeze token sebagai cadangan.";
        }
        return text;
    }

    @Override public String periodKey(Clock clock) { return today(clock).toString(); }

    @Override public NotificationAction action() { return NotificationAction.ADD_TRANSACTION; }
}
