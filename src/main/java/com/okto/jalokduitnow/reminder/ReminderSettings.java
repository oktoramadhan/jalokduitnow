package com.okto.jalokduitnow.reminder;

import com.okto.jalokduitnow.exception.InvalidSettingException;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

/**
 * All the reminder options in one validated object: which reminders are on, at what time, which day of the month,
 * the budget warning percentage, quiet hours and whether to also use the system tray.
 * Encapsulated: every change goes through a method that checks the value and throws InvalidSettingException,
 * so the object can never hold an impossible setting (day 40, equal quiet-hour times...). Times are whole minutes.
 */
public class ReminderSettings {
    public static final int MIN_WARN_PERCENT = 50;
    public static final int MAX_WARN_PERCENT = 99;

    private final Map<ReminderKind, Boolean> enabled = new EnumMap<>(ReminderKind.class);
    private LocalTime dailyLogTime = LocalTime.of(20, 0);
    private LocalTime streakRiskTime = LocalTime.of(21, 0);
    private int donorReportDay = 1;
    private LocalTime donorReportTime = LocalTime.of(9, 0);
    private int budgetWarnPercent = 80;
    private boolean quietEnabled = true;
    private LocalTime quietStart = LocalTime.of(22, 0);
    private LocalTime quietEnd = LocalTime.of(7, 0);
    private boolean trayEnabled = true;

    public ReminderSettings() {
        for (ReminderKind k : ReminderKind.values()) enabled.put(k, k.enabledByDefault());
    }

    /** An independent copy: callers that only read get a snapshot and cannot change the live settings. */
    public ReminderSettings copy() {
        ReminderSettings c = new ReminderSettings();
        c.enabled.putAll(enabled);
        c.dailyLogTime = dailyLogTime;
        c.streakRiskTime = streakRiskTime;
        c.donorReportDay = donorReportDay;
        c.donorReportTime = donorReportTime;
        c.budgetWarnPercent = budgetWarnPercent;
        c.quietEnabled = quietEnabled;
        c.quietStart = quietStart;
        c.quietEnd = quietEnd;
        c.trayEnabled = trayEnabled;
        return c;
    }

    // ---- reading ----

    public boolean isEnabled(ReminderKind kind) { return enabled.get(kind); }
    public LocalTime dailyLogTime() { return dailyLogTime; }
    public LocalTime streakRiskTime() { return streakRiskTime; }
    public int donorReportDay() { return donorReportDay; }
    public LocalTime donorReportTime() { return donorReportTime; }
    public int budgetWarnPercent() { return budgetWarnPercent; }
    public boolean isQuietEnabled() { return quietEnabled; }
    public LocalTime quietStart() { return quietStart; }
    public LocalTime quietEnd() { return quietEnd; }
    public boolean isTrayEnabled() { return trayEnabled; }

    /** True if reminders must stay silent at this time of day. Quiet hours may cross midnight (22:00 to 07:00). */
    public boolean isQuiet(LocalTime time) {
        if (!quietEnabled) return false;
        if (quietStart.isBefore(quietEnd)) return !time.isBefore(quietStart) && time.isBefore(quietEnd);
        return !time.isBefore(quietStart) || time.isBefore(quietEnd);   // wraps past midnight
    }

    // ---- changing (each one validates) ----

    public void setEnabled(ReminderKind kind, boolean value) {
        enabled.put(kind, value);
    }

    public void setDailyLogTime(LocalTime time) throws InvalidSettingException {
        this.dailyLogTime = requireTime(time, "Jam pengingat mencatat");
    }

    public void setStreakRiskTime(LocalTime time) throws InvalidSettingException {
        this.streakRiskTime = requireTime(time, "Jam penjaga streak");
    }

    public void setDonorReport(int dayOfMonth, LocalTime time) throws InvalidSettingException {
        if (dayOfMonth < 1 || dayOfMonth > 31) throw new InvalidSettingException("Tanggal harus antara 1 dan 31");
        LocalTime checked = requireTime(time, "Jam laporan donor");
        this.donorReportDay = dayOfMonth;
        this.donorReportTime = checked;
    }

    public void setBudgetWarnPercent(int percent) throws InvalidSettingException {
        if (percent < MIN_WARN_PERCENT || percent > MAX_WARN_PERCENT) {
            throw new InvalidSettingException("Ambang peringatan budget harus antara " + MIN_WARN_PERCENT + "% dan " + MAX_WARN_PERCENT + "%");
        }
        this.budgetWarnPercent = percent;
    }

    public void setQuietHours(boolean enabled, LocalTime start, LocalTime end) throws InvalidSettingException {
        LocalTime s = requireTime(start, "Jam tenang mulai");
        LocalTime e = requireTime(end, "Jam tenang selesai");
        if (enabled && s.equals(e)) throw new InvalidSettingException("Jam tenang mulai dan selesai tidak boleh sama");
        this.quietEnabled = enabled;
        this.quietStart = s;
        this.quietEnd = e;
    }

    public void setTrayEnabled(boolean value) {
        this.trayEnabled = value;
    }

    private static LocalTime requireTime(LocalTime time, String what) throws InvalidSettingException {
        if (time == null) throw new InvalidSettingException(what + " wajib diisi");
        return time.truncatedTo(ChronoUnit.MINUTES);
    }
}
