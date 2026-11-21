package com.okto.jalokduitnow.achievement;

import java.util.List;

/** The badges that exist in the game, in display order. */
public final class AchievementCatalog {
    private AchievementCatalog() {}

    public static List<Achievement> all() {
        return List.of(
                new StreakAchievement(7, "Seminggu Konsisten"),
                new StreakAchievement(14, "Dua Minggu Rajin"),
                new StreakAchievement(30, "Sebulan Penuh"),
                new BudgetAchievement(),
                new SavingsAchievement(50, "Setengah Jalan", "star"),
                new SavingsAchievement(100, "Target Tercapai", "trophy"),
                new ColumnBuilderAchievement(1),
                new CategoryCustomizerAchievement(3),
                new TransparencyAchievement(),
                new ConsistentAchievement(7));
    }
}
