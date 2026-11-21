package com.okto.jalokduitnow.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class GameProfileTest {
    // Monday 2026-11-16 starts the test week
    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    private static LocalDate day(int offset) { return MON.plusDays(offset); }

    // ---- level curve ----

    @Test
    void levelCurveIs100TimesNToThePowerOnePointFive() {
        assertEquals(100, GameProfile.xpForLevel(1));
        assertEquals(283, GameProfile.xpForLevel(2));   // 282.84 rounded
        assertEquals(520, GameProfile.xpForLevel(3));   // 519.6 rounded
        assertEquals(800, GameProfile.xpForLevel(4));
    }

    @Test
    void levelUpBoundaries() {
        GameProfile p = new GameProfile();
        assertEquals(1, p.getLevel());

        LevelUpResult none = p.addXp(99);
        assertFalse(none.leveledUp());
        assertEquals(1, p.getLevel());
        assertEquals(99, p.xpIntoLevel());

        LevelUpResult up = p.addXp(1);             // exactly 100 XP: level 2
        assertTrue(up.leveledUp());
        assertEquals(1, up.fromLevel());
        assertEquals(2, up.toLevel());
        assertEquals(0, p.xpIntoLevel());
        assertEquals(283, p.xpForNextLevel());

        p.addXp(282);                                // 1 short of level 3
        assertEquals(2, p.getLevel());
        assertTrue(p.addXp(1).leveledUp());
        assertEquals(3, p.getLevel());
    }

    @Test
    void oneBigAwardCanSkipLevels() {
        GameProfile p = new GameProfile();
        LevelUpResult r = p.addXp(100 + 283 + 520);  // exactly enough for level 4
        assertEquals(1, r.fromLevel());
        assertEquals(4, r.toLevel());
        assertEquals("Pemburu Receh", r.title());
    }

    @Test
    void titlesAreIndonesianAndCapAtTheLastOne() {
        assertEquals("Jalok Pemula", GameProfile.titleFor(1));
        assertEquals("Jalok Pemula", GameProfile.titleFor(3));
        assertEquals("Pemburu Receh", GameProfile.titleFor(4));
        assertEquals("Penjaga Dompet", GameProfile.titleFor(7));
        assertEquals("Juragan Hemat", GameProfile.titleFor(10));
        assertEquals("Sultan Jalok", GameProfile.titleFor(13));
        assertEquals("Jalok Abadi", GameProfile.titleFor(500));
    }

    @Test
    void invariantsAreAsserted() {
        GameProfile p = new GameProfile();
        assertThrows(AssertionError.class, () -> p.addXp(-1));          // tests run with -ea
        assertThrows(IllegalArgumentException.class,
                () -> GameProfile.restore(-5, 0, 0, 0, null, null, null, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> GameProfile.restore(0, 5, 3, 0, null, null, null, null, 0));   // longest < current
        assertTrue(p.getXp() >= 0 && p.getLevel() >= 1);
    }

    // ---- streak ----

    @Test
    void consecutiveDaysBuildTheStreak() {
        GameProfile p = new GameProfile();
        p.recordActivity(day(0));
        p.recordActivity(day(1));
        StreakChange c = p.recordActivity(day(2));
        assertEquals(3, p.getStreak());
        assertEquals(3, p.getLongestStreak());
        assertEquals(2, c.before());
        assertEquals(3, c.after());
        assertFalse(c.broken());
    }

    @Test
    void severalEntriesOnOneDayCountOnce() {
        GameProfile p = new GameProfile();
        p.recordActivity(day(0));
        StreakChange again = p.recordActivity(day(0));
        assertEquals(1, p.getStreak());
        assertFalse(again.changed());
        assertTrue(p.hasActivityOn(day(0)));
    }

    @Test
    void aMissedDayIsSavedByAFreezeToken() {
        GameProfile p = new GameProfile();
        p.recordActivity(day(0));   // Mon: also grants this week's token
        p.recordActivity(day(1));   // Tue
        p.recordActivity(day(2));   // Wed -> streak 3
        assertEquals(1, p.getFreezeTokens());

        StreakChange c = p.recordActivity(day(4));   // Fri: Thursday was missed
        assertEquals(1, c.freezeTokensUsed());
        assertFalse(c.broken());
        assertEquals(4, p.getStreak());              // 3 kept, +1 for Friday (Thursday itself is not counted)
        assertEquals(0, p.getFreezeTokens());
    }

    @Test
    void withoutATokenAMissedDayBreaksTheStreak() {
        GameProfile p = new GameProfile();
        p.recordActivity(day(0));
        p.recordActivity(day(1));
        p.recordActivity(day(2));
        p.recordActivity(day(4));                    // spends the only token (streak 4)
        assertEquals(0, p.getFreezeTokens());

        StreakChange c = p.recordActivity(day(7));   // Mon of the next week: Sat+Sun missed, only 1 new token
        assertTrue(c.broken());
        assertEquals(1, p.getStreak());              // starts again today
        assertEquals(4, p.getLongestStreak());       // the record stays
    }

    @Test
    void aGapLongerThanTheTokensBreaksEvenIfTokensExist() {
        GameProfile p = new GameProfile();
        p.recordActivity(day(0));
        p.recordActivity(day(1));
        // two missed days (Wed, Thu) but only one token
        StreakChange c = p.recordActivity(day(4));
        assertTrue(c.broken());
        assertEquals(1, p.getStreak());
        assertEquals(1, p.getFreezeTokens(), "tokens are not wasted on a streak that cannot be saved");
    }

    @Test
    void oneNewTokenEachWeekUpToTheCap() {
        GameProfile p = new GameProfile();
        p.rollTo(day(0));
        assertEquals(1, p.getFreezeTokens());
        p.rollTo(day(3));                            // same week: nothing new
        assertEquals(1, p.getFreezeTokens());
        p.rollTo(day(7));                            // next week
        assertEquals(2, p.getFreezeTokens());
        p.rollTo(day(14));                           // capped
        assertEquals(GameProfile.MAX_FREEZE_TOKENS, p.getFreezeTokens());
    }

    @Test
    void rollingTheSameDayTwiceDoesNotSpendTwice() {
        GameProfile p = new GameProfile();
        p.recordActivity(day(0));
        p.recordActivity(day(1));
        p.rollTo(day(3));                            // Wednesday missed -> token spent, streak kept
        assertEquals(2, p.getStreak());
        int tokens = p.getFreezeTokens();
        StreakChange again = p.rollTo(day(3));
        assertFalse(again.changed());
        assertEquals(tokens, p.getFreezeTokens());
        assertEquals(2, p.getStreak());
    }

    @Test
    void rollingAcrossALongAbsenceBreaksOnce() {
        GameProfile p = new GameProfile();
        p.recordActivity(day(0));
        StreakChange c = p.rollTo(day(30));
        assertTrue(c.broken());
        assertEquals(0, p.getStreak());
        assertFalse(p.rollTo(day(31)).changed());    // already broken: nothing more happens
    }

    // ---- daily entry XP counter, reset ----

    @Test
    void entryXpCounterResetsOnANewDay() {
        GameProfile p = new GameProfile();
        p.noteEntryXp(day(0), 20);
        assertEquals(20, p.entryXpEarnedOn(day(0)));
        assertEquals(0, p.entryXpEarnedOn(day(1)));
        p.noteEntryXp(day(1), 5);
        assertEquals(5, p.entryXpEarnedOn(day(1)));
    }

    @Test
    void resetReturnsToANewProfile() {
        GameProfile p = new GameProfile();
        p.addXp(500);
        p.recordActivity(day(0));
        p.reset();
        assertEquals(0, p.getXp());
        assertEquals(1, p.getLevel());
        assertEquals(0, p.getStreak());
        assertEquals(0, p.getLongestStreak());
        assertNull(p.getLastActiveDate());
    }
}
