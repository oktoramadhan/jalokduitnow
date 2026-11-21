package com.okto.jalokduitnow.achievement;

/** Reach a logging streak of {@code days} days (the longest streak counts, so a later break does not matter). */
public class StreakAchievement extends Achievement {
    private final int days;

    public StreakAchievement(int days, String name) {
        super("streak-" + days, name, "Catat transaksi " + days + " hari berturut-turut", "flame");
        assert days > 0;
        this.days = days;
    }

    public int getDays() { return days; }

    @Override
    public boolean check(GameContext c) {
        return c.profile().getLongestStreak() >= days;
    }
}
