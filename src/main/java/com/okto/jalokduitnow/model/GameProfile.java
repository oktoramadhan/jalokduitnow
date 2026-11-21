package com.okto.jalokduitnow.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * The player: XP, level, daily logging streak and streak-freeze tokens.
 * Encapsulation: there are no public setters. State only changes through addXp, recordActivity, rollTo,
 * noteEntryXp and reset, and the invariants (xp >= 0, level >= 1, streak >= 0) are asserted.
 * Time never comes from the system clock here: callers pass dates, so tests can use fixed days.
 *
 * <p>Level curve: advancing from level n to n+1 costs {@code xpForLevel(n) = round(100 * n^1.5)} XP.
 * Streak: consecutive days with at least one entry created. Each new week grants one freeze token
 * (at most {@value #MAX_FREEZE_TOKENS} are kept); a missed day spends one token instead of breaking the streak.
 */
public class GameProfile {
    public static final int MAX_FREEZE_TOKENS = 2;

    private static final List<String> TITLES = List.of(
            "Jalok Pemula", "Pemburu Receh", "Penjaga Dompet", "Juragan Hemat", "Sultan Jalok",
            "Raja Tabungan", "Maestro Rupiah", "Legenda Dompet", "Dewa Hemat", "Jalok Abadi");
    private static final int LEVELS_PER_TITLE = 3;

    private long xp;
    private int streak;
    private int longestStreak;
    private int freezeTokens;
    private LocalDate lastActiveDate;      // last day an entry was created
    private LocalDate settledThrough;      // every day up to here is accounted for (active or freeze-covered)
    private LocalDate lastTokenWeek;       // Monday of the last week a token was granted
    private LocalDate entryXpDate;         // the day entryXpToday belongs to
    private int entryXpToday;

    public GameProfile() {
        checkInvariants();
    }

    /** Rebuilds a saved profile; invalid numbers are rejected. */
    public static GameProfile restore(long xp, int streak, int longestStreak, int freezeTokens,
                                      LocalDate lastActiveDate, LocalDate settledThrough, LocalDate lastTokenWeek,
                                      LocalDate entryXpDate, int entryXpToday) {
        if (xp < 0 || streak < 0 || longestStreak < streak || freezeTokens < 0 || freezeTokens > MAX_FREEZE_TOKENS
                || entryXpToday < 0) {
            throw new IllegalArgumentException("Data profil tidak valid");
        }
        GameProfile p = new GameProfile();
        p.xp = xp;
        p.streak = streak;
        p.longestStreak = longestStreak;
        p.freezeTokens = freezeTokens;
        p.lastActiveDate = lastActiveDate;
        p.settledThrough = settledThrough;
        p.lastTokenWeek = lastTokenWeek;
        p.entryXpDate = entryXpDate;
        p.entryXpToday = entryXpToday;
        p.checkInvariants();
        return p;
    }

    // ---- level curve ----

    /** XP needed to advance from level n to level n+1. */
    public static long xpForLevel(int n) {
        assert n >= 1;
        return Math.round(100 * Math.pow(n, 1.5));
    }

    public long getXp() { return xp; }

    public int getLevel() {
        int level = 1;
        long remaining = xp;
        while (remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level);
            level++;
        }
        return level;
    }

    /** XP collected inside the current level. */
    public long xpIntoLevel() {
        int level = 1;
        long remaining = xp;
        while (remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level);
            level++;
        }
        return remaining;
    }

    public long xpForNextLevel() {
        return xpForLevel(getLevel());
    }

    /** 0.0 - 1.0 progress through the current level. */
    public double levelProgress() {
        return (double) xpIntoLevel() / xpForNextLevel();
    }

    public String getTitle() {
        return titleFor(getLevel());
    }

    public static String titleFor(int level) {
        return TITLES.get(Math.min((level - 1) / LEVELS_PER_TITLE, TITLES.size() - 1));
    }

    /** Adds XP and reports whether that raised the level. */
    public LevelUpResult addXp(int amount) {
        assert amount >= 0 : "XP amount must not be negative";
        int before = getLevel();
        xp += Math.max(0, amount);
        int after = getLevel();
        checkInvariants();
        return new LevelUpResult(before, after, titleFor(after));
    }

    // ---- streak ----

    public int getStreak() { return streak; }
    public int getLongestStreak() { return longestStreak; }
    public int getFreezeTokens() { return freezeTokens; }
    public LocalDate getLastActiveDate() { return lastActiveDate; }
    public LocalDate getSettledThrough() { return settledThrough; }
    public LocalDate getLastTokenWeek() { return lastTokenWeek; }
    public LocalDate getEntryXpDate() { return entryXpDate; }
    public int getEntryXpToday() { return entryXpToday; }

    public boolean hasActivityOn(LocalDate day) {
        return day.equals(lastActiveDate);
    }

    /**
     * Moves the streak forward to {@code today} without any new activity: grants this week's token if due,
     * then settles every missed day since the last settled day. Missed days are paid with freeze tokens;
     * if there are not enough tokens the streak resets to 0. Safe to call many times a day.
     */
    public StreakChange rollTo(LocalDate today) {
        int before = streak;
        int used = 0;
        boolean broken = false;

        LocalDate thisWeek = weekStart(today);
        if (lastTokenWeek == null || thisWeek.isAfter(lastTokenWeek)) {
            lastTokenWeek = thisWeek;
            freezeTokens = Math.min(MAX_FREEZE_TOKENS, freezeTokens + 1);
        }

        if (settledThrough != null) {
            long missed = ChronoUnit.DAYS.between(settledThrough, today) - 1; // whole days between, today excluded
            if (missed > 0) {
                if (streak > 0 && missed <= freezeTokens) {
                    freezeTokens -= (int) missed;
                    used = (int) missed;
                } else if (streak > 0) {
                    streak = 0;
                    broken = true;
                }
                settledThrough = today.minusDays(1);
            }
        }
        checkInvariants();
        return new StreakChange(before, streak, used, broken);
    }

    /**
     * An entry was created on {@code day}. Settles missed days first, then counts the day once.
     * A second entry on the same day changes nothing.
     */
    public StreakChange recordActivity(LocalDate day) {
        StreakChange rolled = rollTo(day);
        if (day.equals(lastActiveDate)) return rolled;
        streak++;
        longestStreak = Math.max(longestStreak, streak);
        lastActiveDate = day;
        settledThrough = day;
        checkInvariants();
        return new StreakChange(rolled.before(), streak, rolled.freezeTokensUsed(), rolled.broken());
    }

    // ---- daily entry XP cap ----

    /** Entry XP already earned on this day (0 for a new day). */
    public int entryXpEarnedOn(LocalDate day) {
        return day.equals(entryXpDate) ? entryXpToday : 0;
    }

    public void noteEntryXp(LocalDate day, int amount) {
        assert amount >= 0;
        if (!day.equals(entryXpDate)) {
            entryXpDate = day;
            entryXpToday = 0;
        }
        entryXpToday += amount;
    }

    /** Back to a brand-new profile. */
    public void reset() {
        xp = 0;
        streak = 0;
        longestStreak = 0;
        freezeTokens = 0;
        lastActiveDate = null;
        settledThrough = null;
        lastTokenWeek = null;
        entryXpDate = null;
        entryXpToday = 0;
        checkInvariants();
    }

    private static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void checkInvariants() {
        assert xp >= 0 : "xp must be >= 0";
        assert getLevel() >= 1 : "level must be >= 1";
        assert streak >= 0 && longestStreak >= streak : "streak invariants";
        assert freezeTokens >= 0 && freezeTokens <= MAX_FREEZE_TOKENS : "freeze token range";
    }
}
