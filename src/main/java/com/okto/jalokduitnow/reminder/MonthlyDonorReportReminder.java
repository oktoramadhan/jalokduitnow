package com.okto.jalokduitnow.reminder;

import java.time.Clock;
import java.time.LocalTime;
import java.time.YearMonth;

/**
 * On a chosen day of the month, at a chosen time: time to prepare the donor report. Once per month.
 * A day the month does not have is moved to the month's last day (day 31 becomes the 28th, or 29th in a leap
 * year, in February). It only fires ON that day: if the app was closed on the day, the reminder is not replayed
 * later, because a reminder older than today is no longer useful. It needs at least one recipient to be useful.
 */
public class MonthlyDonorReportReminder extends Reminder {
    public static final String ID = "donor-report";
    private final int dayOfMonth;
    private final LocalTime at;

    public MonthlyDonorReportReminder(boolean enabled, int dayOfMonth, LocalTime at) {
        super(ID, "Laporan donor", enabled);
        this.dayOfMonth = dayOfMonth;
        this.at = at;
    }

    /** The day of the month this reminder really fires on in the given month. */
    public int effectiveDay(YearMonth month) {
        return Math.min(dayOfMonth, month.lengthOfMonth());
    }

    @Override
    public boolean shouldFire(ReminderContext context, Clock clock) {
        var today = today(clock);
        return isEnabled() && !context.recipients().isEmpty()
                && today.getDayOfMonth() == effectiveDay(YearMonth.from(today))
                && reached(clock, at);
    }

    @Override
    public String buildMessage(ReminderContext context) {
        String who = context.defaultRecipient().map(r -> " untuk " + r.getName()).orElse("");
        return "Hari ini waktunya menyiapkan laporan donor" + who + ". Buka halaman Laporan untuk meninjau dan mengirimnya.";
    }

    @Override public String periodKey(Clock clock) { return YearMonth.now(clock).toString(); }

    @Override public NotificationAction action() { return NotificationAction.OPEN_REPORT; }
}
