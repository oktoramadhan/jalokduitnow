package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.achievement.Achievement;
import com.okto.jalokduitnow.achievement.ConsistentAchievement;
import com.okto.jalokduitnow.achievement.GameContext;
import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.model.Expense;
import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.model.XpPolicy;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.storage.GameStateDto;
import com.okto.jalokduitnow.storage.JsonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The "Konsisten" badge: 7 days in a row after a reminder fired, with a one-time bonus. Start: Monday 2026-11-16. */
class ConsistentBadgeTest {
    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    @TempDir Path dir;
    TestClock clock;
    Fixture fx;
    GameEventBus bus;
    GameService game;
    List<GameEvent> events;
    UUID food;

    @BeforeEach
    void setUp() throws Exception {
        clock = new TestClock(MON);
        fx = Fixture.in(dir, clock);
        bus = new GameEventBus();
        fx.finance.setEventBus(bus);
        game = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx.finance, fx.categories, fx.schema, bus, new XpPolicy(), clock);
        game.load();
        game.rollDay();
        events = new ArrayList<>();
        bus.subscribe(GameEvent.class, events::add);
        food = fx.expense("Makan");
    }

    private void logOn(LocalDate day) throws Exception {
        clock.set(day);
        fx.finance.add(new Expense(day, food, 1_000));
    }

    private void reminderFires() {
        bus.publish(new GameEvent.ReminderFired("daily-log"));
    }

    private boolean unlocked() {
        Achievement a = game.achievements().stream().filter(x -> x.getId().equals("consistent")).findFirst().orElseThrow();
        return game.unlockedOn(a).isPresent();
    }

    @Test
    void sevenDaysInARowAfterAReminderUnlocksTheBadgeWithABonus() throws Exception {
        reminderFires();                                           // on Monday the 16th
        for (int i = 0; i < 6; i++) logOn(MON.plusDays(i));
        assertFalse(unlocked(), "6 days is not enough");

        long before = game.profile().getXp();
        logOn(MON.plusDays(6));                                    // the 7th day
        assertTrue(unlocked());
        // 7th entry: 5 entry + 3 complete + 10 first-of-day = 18; the 7-day streak badge (100) and this badge (100 + 25 bonus)
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.XpGained g && g.amount() == 125 && g.reason().contains("Badge")),
                "badge XP (100) plus the one-time bonus (25) in one award");
        assertEquals(before + 18 + 100 + 125, game.profile().getXp());
        List<String> unlockedIds = events.stream().filter(e -> e instanceof GameEvent.BadgeUnlocked)
                .map(e -> ((GameEvent.BadgeUnlocked) e).achievement().getId()).toList();
        assertTrue(unlockedIds.contains("consistent"));
        assertTrue(unlockedIds.contains("streak-7"), "the plain 7-day streak badge unlocks on the same day");
    }

    @Test
    void theBonusIsPaidOnlyOnce() throws Exception {
        reminderFires();
        for (int i = 0; i < 9; i++) logOn(MON.plusDays(i));        // keep going past 7 days
        assertEquals(1, events.stream().filter(e -> e instanceof GameEvent.BadgeUnlocked b
                && b.achievement().getId().equals("consistent")).count());
        assertEquals(1, events.stream().filter(e -> e instanceof GameEvent.XpGained g && g.amount() == 125).count());
    }

    @Test
    void aStreakThatWasAlreadyRunningBeforeTheReminderDoesNotCount() throws Exception {
        for (int i = 0; i < 7; i++) logOn(MON.plusDays(i));        // 7 days with no reminder at all
        assertFalse(unlocked(), "no reminder has fired yet");
        clock.set(MON.plusDays(7));
        reminderFires();                                           // the first reminder appears on day 8
        logOn(MON.plusDays(7));                                    // streak is 8 but it began before the reminder
        assertFalse(unlocked(), "the badge is for building the habit after the reminder");
    }

    @Test
    void withoutAnyReminderThereIsNoBadgeEvenWithALongStreak() throws Exception {
        for (int i = 0; i < 10; i++) logOn(MON.plusDays(i));
        assertFalse(unlocked());
    }

    @Test
    void onlyTheFirstReminderStartsTheClockAndALaterOneDoesNotMoveIt() throws Exception {
        reminderFires();                                           // day 1
        for (int i = 0; i < 3; i++) logOn(MON.plusDays(i));
        clock.set(MON.plusDays(3));
        reminderFires();                                           // another reminder later: must not restart the count
        for (int i = 3; i < 7; i++) logOn(MON.plusDays(i));
        assertTrue(unlocked());
    }

    @Test
    void aBrokenStreakNeedsAFreshSevenDays() throws Exception {
        reminderFires();
        for (int i = 0; i < 3; i++) logOn(MON.plusDays(i));
        // a long gap breaks the streak (more missed days than freeze tokens)
        for (int i = 10; i < 16; i++) logOn(MON.plusDays(i));
        assertFalse(unlocked(), "6 days since the break");
        logOn(MON.plusDays(16));
        assertTrue(unlocked(), "the new streak began after the reminder too");
    }

    @Test
    void theStartDayIsSavedAndResetClearsIt() throws Exception {
        reminderFires();
        game.flush();
        fx.flush();
        Fixture fx2 = fx.reload();
        GameService again = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx2.finance, fx2.categories, fx2.schema, new GameEventBus(), new XpPolicy(), clock);
        again.load();
        // the saved state still knows a reminder fired: 7 days after it still unlock the badge in the new session
        GameEventBus bus2 = new GameEventBus();
        GameService session2 = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx2.finance, fx2.categories, fx2.schema, bus2, new XpPolicy(), clock);
        fx2.finance.setEventBus(bus2);
        session2.load();
        UUID cat = fx2.expense("Makan");
        for (int i = 0; i < 7; i++) {
            clock.set(MON.plusDays(i));
            fx2.finance.add(new Expense(MON.plusDays(i), cat, 1_000));
        }
        Achievement badge = session2.achievements().stream().filter(x -> x.getId().equals("consistent")).findFirst().orElseThrow();
        assertTrue(session2.unlockedOn(badge).isPresent());

        session2.reset();
        assertTrue(session2.unlockedOn(badge).isEmpty(), "reset forgets the badge and the reminder start day");
    }

    // ---- the rule on its own ----

    private GameContext ctx(GameProfile p, LocalDate firstReminder) {
        return new GameContext(p, List.of(), new TableSchema(), List.of(), List.of(), clock, false, firstReminder);
    }

    @Test
    void theRuleLooksAtTheStreakStartAgainstTheFirstReminderDay() {
        Achievement a = new ConsistentAchievement(7);
        LocalDate lastActive = LocalDate.of(2026, 11, 30);
        GameProfile streak7 = GameProfile.restore(0, 7, 7, 0, lastActive, lastActive, null, null, 0);     // began 24 Nov
        assertTrue(a.check(ctx(streak7, LocalDate.of(2026, 11, 24))), "began on the reminder day");
        assertTrue(a.check(ctx(streak7, LocalDate.of(2026, 11, 20))), "began after the reminder");
        assertFalse(a.check(ctx(streak7, LocalDate.of(2026, 11, 25))), "began before the reminder");
        assertFalse(a.check(ctx(streak7, null)), "no reminder yet");
        GameProfile streak6 = GameProfile.restore(0, 6, 6, 0, lastActive, lastActive, null, null, 0);
        assertFalse(a.check(ctx(streak6, LocalDate.of(2026, 11, 1))));
        assertFalse(a.check(ctx(new GameProfile(), LocalDate.of(2026, 11, 1))));
        assertEquals(25, a.bonusXp());
        assertEquals("check", a.getIconKey());
        assertEquals(0, new com.okto.jalokduitnow.achievement.StreakAchievement(7, "x").bonusXp(), "other badges have no bonus");
    }
}
