package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.InvalidSettingException;
import com.okto.jalokduitnow.reminder.ReminderKind;
import com.okto.jalokduitnow.reminder.ReminderSettings;

import java.time.LocalTime;
import java.util.Map;

/**
 * JSON form of ReminderSettings (reminders.json). Missing fields (an older file) fall back to the defaults.
 * Reading goes back through the validating setters, so a hand-edited file with day 40 is rejected, not trusted.
 */
public record ReminderSettingsDto(Map<String, Boolean> enabled, LocalTime dailyLogTime, LocalTime streakRiskTime,
                                  Integer donorReportDay, LocalTime donorReportTime, Integer budgetWarnPercent,
                                  Boolean quietEnabled, LocalTime quietStart, LocalTime quietEnd, Boolean trayEnabled) {

    public static ReminderSettingsDto from(ReminderSettings s) {
        Map<String, Boolean> enabled = new java.util.LinkedHashMap<>();
        for (ReminderKind k : ReminderKind.values()) enabled.put(k.name(), s.isEnabled(k));
        return new ReminderSettingsDto(enabled, s.dailyLogTime(), s.streakRiskTime(), s.donorReportDay(),
                s.donorReportTime(), s.budgetWarnPercent(), s.isQuietEnabled(), s.quietStart(), s.quietEnd(), s.isTrayEnabled());
    }

    public ReminderSettings toSettings() throws InvalidSettingException {
        ReminderSettings s = new ReminderSettings();
        if (enabled != null) {
            for (ReminderKind k : ReminderKind.values()) {
                Boolean value = enabled.get(k.name());
                if (value != null) s.setEnabled(k, value);
            }
        }
        if (dailyLogTime != null) s.setDailyLogTime(dailyLogTime);
        if (streakRiskTime != null) s.setStreakRiskTime(streakRiskTime);
        if (donorReportDay != null || donorReportTime != null) {
            s.setDonorReport(donorReportDay == null ? s.donorReportDay() : donorReportDay,
                    donorReportTime == null ? s.donorReportTime() : donorReportTime);
        }
        if (budgetWarnPercent != null) s.setBudgetWarnPercent(budgetWarnPercent);
        if (quietEnabled != null || quietStart != null || quietEnd != null) {
            s.setQuietHours(quietEnabled == null ? s.isQuietEnabled() : quietEnabled,
                    quietStart == null ? s.quietStart() : quietStart, quietEnd == null ? s.quietEnd() : quietEnd);
        }
        if (trayEnabled != null) s.setTrayEnabled(trayEnabled);
        return s;
    }
}
