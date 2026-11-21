package com.okto.jalokduitnow.reminder;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds this moment's reminders from the settings: the three fixed ones plus a pair (warning level and budget
 * reached) for every active expense category that has a monthly budget. Called on every check, so a changed setting
 * or a new budget takes effect right away.
 */
public final class ReminderFactory {
    private static final int BUDGET_REACHED_PERCENT = 100;

    private ReminderFactory() {}

    public static List<Reminder> build(ReminderSettings settings, ReminderContext context) {
        List<Reminder> reminders = new ArrayList<>();
        reminders.add(new DailyLogReminder(settings.isEnabled(ReminderKind.DAILY_LOG), settings.dailyLogTime()));
        reminders.add(new StreakRiskReminder(settings.isEnabled(ReminderKind.STREAK_RISK), settings.streakRiskTime()));
        reminders.add(new MonthlyDonorReportReminder(settings.isEnabled(ReminderKind.DONOR_REPORT),
                settings.donorReportDay(), settings.donorReportTime()));
        boolean budgetOn = settings.isEnabled(ReminderKind.BUDGET);
        for (Category c : context.categories()) {
            if (c.getType() != CategoryType.EXPENSE || c.isArchived() || c.getMonthlyBudget() == null) continue;
            reminders.add(new BudgetThresholdReminder(budgetOn, c, settings.budgetWarnPercent(), true));
            reminders.add(new BudgetThresholdReminder(budgetOn, c, BUDGET_REACHED_PERCENT, false));
        }
        return reminders;
    }
}
