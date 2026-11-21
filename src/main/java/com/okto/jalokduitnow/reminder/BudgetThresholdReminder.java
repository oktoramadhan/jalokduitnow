package com.okto.jalokduitnow.reminder;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.util.Rupiah;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Tells the user when spending in a category reaches a share of its monthly budget. One instance per category and
 * threshold, so "once per threshold per month" is simply once per (id, month). The warning threshold (for example 80%)
 * only applies while spending is still under 100%: if one big expense jumps straight past the budget, the "budget
 * reached" notification is the useful one and the warning would only be noise.
 */
public class BudgetThresholdReminder extends Reminder {
    private final UUID categoryId;
    private final String categoryName;
    private final long monthlyBudget;
    private final int percent;
    private final boolean capsAtBudget;   // true for the warning level: stops applying once the budget is reached

    /**
     * @param percent      the share of the budget that triggers it (80 or 100)
     * @param capsAtBudget true to stop applying at 100% (used for the warning level)
     */
    public BudgetThresholdReminder(boolean enabled, Category category, int percent, boolean capsAtBudget) {
        super("budget:" + category.getId() + ":" + percent, "Budget " + category.getName(), enabled);
        assert category.getMonthlyBudget() != null && percent > 0;
        this.categoryId = category.getId();
        this.categoryName = category.getName();
        this.monthlyBudget = category.getMonthlyBudget();
        this.percent = percent;
        this.capsAtBudget = capsAtBudget;
    }

    @Override
    public boolean shouldFire(ReminderContext context, Clock clock) {
        if (!isEnabled()) return false;
        long spent = spentThisMonth(context, clock);
        boolean reached = spent * 100 >= monthlyBudget * percent;
        boolean stillBelowBudget = spent < monthlyBudget;
        return reached && (!capsAtBudget || stillBelowBudget);
    }

    @Override
    public String buildMessage(ReminderContext context) {
        long spent = context.expenseIn(categoryId, context.month());
        String figures = "(" + Rupiah.format(spent) + " dari " + Rupiah.format(monthlyBudget) + ")";
        if (percent >= 100) {
            return "Pengeluaran " + categoryName + " bulan ini sudah mencapai budget " + figures
                    + ". Tidak apa-apa; terus catat agar gambaranmu tetap jelas.";
        }
        return "Pengeluaran " + categoryName + " sudah " + percent + "% dari budget bulan ini " + figures
                + ". Masih ada ruang untuk sisa bulan ini.";
    }

    @Override public String periodKey(Clock clock) { return YearMonth.now(clock).toString(); }

    @Override public NotificationAction action() { return NotificationAction.OPEN_CATEGORIES; }

    private long spentThisMonth(ReminderContext context, Clock clock) {
        return context.expenseIn(categoryId, YearMonth.now(clock));
    }
}
