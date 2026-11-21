package com.okto.jalokduitnow.achievement;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.model.Transaction;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Finish the previous full month with every category that has a monthly budget still within it. */
public class BudgetAchievement extends Achievement {
    public BudgetAchievement() {
        super("budget-month", "Anggaran Terjaga",
                "Selesaikan satu bulan penuh tanpa melewati budget kategori mana pun", "shield");
    }

    @Override
    public boolean check(GameContext c) {
        YearMonth lastMonth = YearMonth.from(LocalDate.now(c.clock())).minusMonths(1);
        List<Category> budgeted = c.categories().stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE && cat.getMonthlyBudget() != null).toList();
        if (budgeted.isEmpty()) return false;
        // needs real activity in that month, otherwise an empty month would unlock it for free
        boolean hadActivity = c.transactions().stream().anyMatch(t -> YearMonth.from(t.getDate()).equals(lastMonth));
        if (!hadActivity) return false;
        for (Category cat : budgeted) {
            long spent = c.transactions().stream()
                    .filter(t -> t.signedAmount() < 0 && t.getCategoryId().equals(cat.getId())
                            && YearMonth.from(t.getDate()).equals(lastMonth))
                    .mapToLong(Transaction::getAmount).sum();
            if (spent > cat.getMonthlyBudget()) return false;
        }
        return true;
    }
}
