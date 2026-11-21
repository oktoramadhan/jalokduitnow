package com.okto.jalokduitnow.reminder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Something that can nudge the user. Abstraction: each subclass answers two questions, "should I fire now?" and
 * "what do I say?", and names the period it belongs to (a day or a month). The service fires a reminder at most
 * once per {@code id + period}, so the subclasses never have to remember anything.
 * A reminder is an immutable value built from the current settings on every check.
 * Tone: encouraging and neutral, never guilt or shame.
 */
public abstract class Reminder {
    private final String id;
    private final String title;
    private final boolean enabled;

    protected Reminder(String id, String title, boolean enabled) {
        assert id != null && !id.isBlank() && title != null;
        this.id = id;
        this.title = title;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public boolean isEnabled() { return enabled; }

    /** True if the reminder is due right now (by the clock's date, time and zone) and nothing makes it pointless. */
    public abstract boolean shouldFire(ReminderContext context, Clock clock);

    /** The text of the notification. */
    public abstract String buildMessage(ReminderContext context);

    /** The period a firing belongs to: today's date for daily reminders, the month for monthly ones. */
    public abstract String periodKey(Clock clock);

    /** The button shown on the notification. */
    public NotificationAction action() {
        return NotificationAction.NONE;
    }

    // ---- helpers ----

    protected static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }

    protected static LocalTime timeNow(Clock clock) {
        return LocalDateTime.now(clock).toLocalTime();
    }

    /** True once the time of day has reached {@code at} (so a reminder missed earlier today still fires). */
    protected static boolean reached(Clock clock, LocalTime at) {
        return !timeNow(clock).isBefore(at);
    }
}
