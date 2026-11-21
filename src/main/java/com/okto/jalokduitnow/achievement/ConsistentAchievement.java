package com.okto.jalokduitnow.achievement;

import java.time.LocalDate;

/**
 * Log on {@code days} days in a row after a reminder has fired. The streak must have started on or after the day
 * the first reminder appeared, so the badge rewards building the habit with the reminder's help, not a streak
 * that was already running. Gives a one-time bonus on top of the usual badge XP.
 */
public class ConsistentAchievement extends Achievement {
    public static final int BONUS_XP = 25;
    private final int days;

    public ConsistentAchievement(int days) {
        super("consistent", "Konsisten", "Catat " + days + " hari berturut-turut setelah pengingat pertama muncul", "check");
        assert days > 0;
        this.days = days;
    }

    @Override
    public boolean check(GameContext c) {
        LocalDate firstReminder = c.firstReminderDay();
        LocalDate lastActive = c.profile().getLastActiveDate();
        if (firstReminder == null || lastActive == null || c.profile().getStreak() < days) return false;
        LocalDate streakStart = lastActive.minusDays(c.profile().getStreak() - 1L);
        return !streakStart.isBefore(firstReminder);
    }

    @Override
    public int bonusXp() {
        return BONUS_XP;
    }
}
