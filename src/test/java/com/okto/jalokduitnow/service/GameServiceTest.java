package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.achievement.Achievement;
import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.exception.InvalidGoalException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.quest.CategoryBudgetQuest;
import com.okto.jalokduitnow.quest.LogDaysQuest;
import com.okto.jalokduitnow.quest.Quest;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TextType;
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

/** Starts on Monday 2026-11-16 (noon, Asia/Jakarta) with the default XP policy unless a test says otherwise. */
class GameServiceTest {
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
        start(new XpPolicy());
    }

    private void start(XpPolicy policy) throws Exception {
        fx = Fixture.in(dir, clock);
        bus = new GameEventBus();
        fx.finance.setEventBus(bus);
        game = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx.finance, fx.categories, fx.schema, bus, policy, clock);
        game.load();
        game.rollDay();
        events = new ArrayList<>();
        bus.subscribe(GameEvent.class, events::add);
        food = fx.expense("Makan");
    }

    private Transaction add(UUID category, long amount) throws Exception {
        Transaction t = new Expense(clock.today(), category, amount);
        fx.finance.add(t);
        return t;
    }

    private <E extends GameEvent> List<E> eventsOf(Class<E> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    // ---- XP rules ----

    @Test
    void firstEntryOfTheDayGivesEntryPlusFirstPlusCompleteBonus() throws Exception {
        add(food, 10_000);
        assertEquals(5 + 10 + 3, game.profile().getXp());
        assertEquals(1, game.profile().getStreak());
        assertEquals(1, eventsOf(GameEvent.TransactionAdded.class).size());
        assertEquals(18, eventsOf(GameEvent.XpGained.class).get(0).amount());
        assertEquals(1, eventsOf(GameEvent.StreakChanged.class).size());
    }

    @Test
    void laterEntriesTheSameDayGetNoFirstOfDayBonus() throws Exception {
        add(food, 1_000);
        long after1 = game.profile().getXp();
        add(food, 1_000);
        assertEquals(after1 + 5 + 3, game.profile().getXp());
    }

    @Test
    void entryXpIsCappedAtThirtyPerDay() throws Exception {
        for (int i = 0; i < 8; i++) add(food, 1_000);
        // entry XP stops at 30; the first-of-day bonus (10) and 8 complete bonuses (3 each) are separate sources
        assertEquals(30 + 10 + 8 * 3, game.profile().getXp());
    }

    @Test
    void theCapResetsOnTheNextDay() throws Exception {
        for (int i = 0; i < 8; i++) add(food, 1_000);
        long before = game.profile().getXp();
        clock.set(MON.plusDays(1));
        add(food, 1_000);
        assertEquals(before + 5 + 10 + 3, game.profile().getXp());
        assertEquals(2, game.profile().getStreak());
    }

    @Test
    void anEntryIsOnlyCompleteWhenEveryRequiredColumnIsFilled() throws Exception {
        ColumnDefinition note = fx.schema.addColumn("Catatan", new TextType(), true);

        add(food, 1_000);                                       // required column empty: no +3
        assertEquals(5 + 10, game.profile().getXp());

        Transaction filled = new Expense(clock.today(), food, 1_000);
        filled.setCustomValue(note, "ok");
        fx.finance.add(filled);
        assertEquals(5 + 10 + 5 + 3, game.profile().getXp());
    }

    @Test
    void editingOrDeletingNeverChangesXp() throws Exception {
        Transaction t = add(food, 1_000);
        long xp = game.profile().getXp();
        int xpEvents = eventsOf(GameEvent.XpGained.class).size();

        Expense edited = new Expense(t.getId(), t.getDate(), t.getCategoryId(), 99_000);
        edited.setCreatedAt(t.getCreatedAt());
        fx.finance.update(edited);
        fx.finance.delete(t.getId());

        assertEquals(xp, game.profile().getXp());
        assertEquals(xpEvents, eventsOf(GameEvent.XpGained.class).size());
        assertEquals(1, eventsOf(GameEvent.TransactionAdded.class).size());   // only the add was announced
    }

    @Test
    void deletingAndReAddingDoesNotFarmFirstOfDayXp() throws Exception {
        Transaction t = add(food, 1_000);
        fx.finance.delete(t.getId());
        long before = game.profile().getXp();
        add(food, 1_000);
        assertEquals(before + 5 + 3, game.profile().getXp());    // no second +10 for the same day
    }

    @Test
    void replaceAllAnnouncesNothing() throws Exception {
        fx.finance.replaceAll(List.of(new Expense(clock.today(), food, 1_000)));
        assertEquals(0, game.profile().getXp());
        assertTrue(eventsOf(GameEvent.TransactionAdded.class).isEmpty());
    }

    // ---- level up ----

    @Test
    void crossingTheLevelBoundaryPublishesLevelUp() throws Exception {
        start(new XpPolicy(50, 1_000, 0, 0, 50, 100));           // 50 XP per entry, nothing else
        add(food, 1_000);
        assertTrue(eventsOf(GameEvent.LevelUp.class).isEmpty());  // 50 XP: still level 1
        add(food, 1_000);                                         // 100 XP: level 2
        List<GameEvent.LevelUp> ups = eventsOf(GameEvent.LevelUp.class);
        assertEquals(1, ups.size());
        assertEquals(2, ups.get(0).result().toLevel());
        assertEquals(2, game.profile().getLevel());
    }

    // ---- streak through the service ----

    @Test
    void streakGrowsAcrossDaysAndAFreezeCoversOneMissedDay() throws Exception {
        add(food, 1_000);                                         // Mon
        clock.set(MON.plusDays(1));
        add(food, 1_000);                                         // Tue
        clock.set(MON.plusDays(3));                               // Thu: Wednesday missed
        add(food, 1_000);
        assertEquals(3, game.profile().getStreak());              // saved by the weekly token
        assertEquals(0, game.profile().getFreezeTokens());
    }

    @Test
    void midnightRollBreaksTheStreakAfterAMissedDayWithoutATokenLeft() throws Exception {
        add(food, 1_000);                                         // Mon
        clock.set(MON.plusDays(1));
        game.rollDay();                                           // Tue midnight: nothing missed yet
        assertEquals(1, game.profile().getStreak());
        clock.set(MON.plusDays(4));
        game.rollDay();                                           // Fri: Tue+Wed+Thu missed, one token
        assertEquals(0, game.profile().getStreak());
        assertEquals(1, game.profile().getLongestStreak());
        assertTrue(eventsOf(GameEvent.StreakChanged.class).stream().anyMatch(e -> e.change().broken()));
    }

    // ---- quests ----

    @Test
    void aQuestPaysItsRewardOnceAndEditsOrDeletesNeverChangeThat() throws Exception {
        LogDaysQuest log = (LogDaysQuest) game.quests().stream().filter(q -> q instanceof LogDaysQuest).findFirst().orElseThrow();
        assertEquals(5, log.getTargetDays());

        List<Transaction> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            clock.set(MON.plusDays(i));
            five.add(add(food, 1_000));
        }
        assertEquals(1, eventsOf(GameEvent.QuestCompleted.class).size());
        assertTrue(game.isRewarded(log));
        assertTrue(game.progressOf(log).isCompleted());
        long xp = game.profile().getXp();

        // delete one entry: progress is recomputed (4 days now), but the reward is neither taken back nor paid again
        fx.finance.delete(five.get(4).getId());
        assertEquals(4, game.progressOf(log).current());
        assertEquals(xp, game.profile().getXp());
        assertTrue(game.isRewarded(log));

        // adding the day again does not pay the quest a second time
        add(food, 1_000);
        assertEquals(1, eventsOf(GameEvent.QuestCompleted.class).size());
    }

    @Test
    void aBudgetQuestIsPaidWhenItsWeekEndsWithinTheLimit() throws Exception {
        fx.categories.setBudget(food, 210_000L);                  // weekly limit 49,000
        clock.set(MON);
        game.reset();                                             // regenerate this week's quests with the budget in place
        CategoryBudgetQuest budget = (CategoryBudgetQuest) game.quests().stream()
                .filter(q -> q instanceof CategoryBudgetQuest).findFirst().orElseThrow();
        assertEquals(49_000, budget.getLimit());

        add(food, 10_000);
        assertTrue(eventsOf(GameEvent.QuestCompleted.class).stream().noneMatch(e -> e.quest().getId().equals(budget.getId())),
                "a budget quest cannot finish before the week is over");

        clock.set(MON.plusDays(7));                               // next Monday, past midnight
        game.rollDay();
        assertTrue(eventsOf(GameEvent.QuestCompleted.class).stream().anyMatch(e -> e.quest().getId().equals(budget.getId())));
        assertTrue(game.isRewarded(budget));
        // and a fresh set of quests for the new week
        Quest first = game.quests().get(0);
        assertEquals(MON.plusDays(7), first.getPeriod().start());
    }

    @Test
    void anOverspentBudgetQuestIsNeverPaid() throws Exception {
        fx.categories.setBudget(food, 210_000L);
        game.reset();
        CategoryBudgetQuest budget = (CategoryBudgetQuest) game.quests().stream()
                .filter(q -> q instanceof CategoryBudgetQuest).findFirst().orElseThrow();
        add(food, 60_000);                                        // over 49,000
        clock.set(MON.plusDays(7));
        game.rollDay();
        assertFalse(game.isRewarded(budget));
    }

    // ---- badges ----

    @Test
    void aBadgeUnlocksOnceGivesXpAndStaysUnlocked() throws Exception {
        long before = game.profile().getXp();
        fx.schema.addColumn("A", new TextType(), false);          // schema already has "Keterangan": 2 columns
        assertTrue(eventsOf(GameEvent.BadgeUnlocked.class).isEmpty());
        ColumnDefinition third = fx.schema.addColumn("B", new TextType(), false);

        List<GameEvent.BadgeUnlocked> unlocked = eventsOf(GameEvent.BadgeUnlocked.class);
        assertEquals(1, unlocked.size());
        assertEquals("column-builder", unlocked.get(0).achievement().getId());
        assertEquals(clock.today(), unlocked.get(0).date());
        assertEquals(before + 100, game.profile().getXp());       // +100 XP for a badge

        game.profile().getXp();
        fx.schema.deleteColumn(third.getId(), true);              // the condition is no longer true...
        Achievement builder = game.achievements().stream().filter(a -> a.getId().equals("column-builder")).findFirst().orElseThrow();
        assertEquals(clock.today(), game.unlockedOn(builder).orElseThrow());   // ...but the badge stays
        fx.schema.addColumn("C", new TextType(), false);
        assertEquals(1, eventsOf(GameEvent.BadgeUnlocked.class).size());       // and is not awarded twice
    }

    // ---- savings goals ----

    @Test
    void savingsGoalsAreValidatedAndTrackProgress() throws Exception {
        UUID tab = fx.expense("Tabungan");
        assertThrows(InvalidGoalException.class, () -> game.addGoal(" ", 100_000, tab));
        assertThrows(InvalidGoalException.class, () -> game.addGoal("Laptop", 0, tab));
        assertThrows(InvalidGoalException.class, () -> game.addGoal("Laptop", 100_000, UUID.randomUUID()));
        assertThrows(InvalidGoalException.class, () -> game.addGoal("Laptop", 100_000, null));

        SavingsGoal goal = game.addGoal("Laptop", 400_000, tab);
        add(tab, 100_000);
        assertEquals(25, game.goalProgress(goal).percent());

        game.updateGoal(goal.id(), "Laptop Baru", 200_000, tab);
        assertEquals(50, game.goalProgress(game.goals().get(0)).percent());
        game.deleteGoal(goal.id());
        assertTrue(game.goals().isEmpty());
    }

    @Test
    void monthlyScoreComesFromTheMonthsMoney() throws Exception {
        fx.finance.add(new Income(clock.today(), fx.income("Gaji"), 1_000_000));
        fx.finance.add(new Expense(clock.today(), food, 600_000));
        assertEquals(MonthlyScore.Grade.A, game.scoreFor(java.time.YearMonth.from(clock.today())).grade());
    }

    // ---- reset ----

    @Test
    void resetStartsOverButKeepsGoalsAndTransactions() throws Exception {
        UUID tab = fx.expense("Tabungan");
        game.addGoal("Laptop", 100_000, tab);
        add(food, 1_000);
        assertTrue(game.profile().getXp() > 0);

        game.reset();
        assertEquals(0, game.profile().getXp());
        assertEquals(0, game.profile().getStreak());
        assertEquals(0, game.unlockedCount());
        assertEquals(1, game.goals().size());
        assertEquals(1, fx.finance.getAll().size());
        assertFalse(game.quests().isEmpty());
    }

    // ---- persistence ----

    @Test
    void gameStateSurvivesARestart() throws Exception {
        UUID tab = fx.expense("Tabungan");
        game.addGoal("Laptop", 100_000, tab);
        for (int i = 0; i < 3; i++) {
            clock.set(MON.plusDays(i));
            add(food, 1_000);
        }
        fx.schema.addColumn("A", new TextType(), false);
        fx.schema.addColumn("B", new TextType(), false);          // unlocks a badge
        game.flush();
        fx.flush();

        Fixture fx2 = fx.reload();
        GameService again = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx2.finance, fx2.categories, fx2.schema, new GameEventBus(), new XpPolicy(), clock);
        again.load();

        assertEquals(game.profile().getXp(), again.profile().getXp());
        assertEquals(game.profile().getLevel(), again.profile().getLevel());
        assertEquals(3, again.profile().getStreak());
        assertEquals(game.unlockedCount(), again.unlockedCount());
        assertEquals(1, again.goals().size());
        assertEquals(game.quests().stream().map(Quest::getId).toList(), again.quests().stream().map(Quest::getId).toList());
    }

    // ---- first run: streak is rebuilt from existing history ----

    @Test
    void theFirstRunRebuildsTheStreakFromExistingEntriesWithoutGivingXp() throws Exception {
        Fixture fresh = Fixture.in(dir.resolve("history"), clock);
        List<Transaction> history = new ArrayList<>();
        for (int i = 0; i < 3; i++) {                              // entries created Mon, Tue, Wed
            Transaction t = new Expense(MON.plusDays(i), fresh.expense("Makan"), 1_000);
            t.setCreatedAt(MON.plusDays(i).atTime(12, 0).atZone(TestClock.ZONE).toInstant());
            history.add(t);
        }
        fresh.finance.replaceAll(history);
        clock.set(MON.plusDays(2));

        var bus2 = new GameEventBus();
        List<GameEvent> seen = new ArrayList<>();
        bus2.subscribe(GameEvent.class, seen::add);
        var path = dir.resolve("history-game.json");
        GameService g = new GameService(new JsonRepository<>(path, GameStateDto.class),
                fresh.finance, fresh.categories, fresh.schema, bus2, new XpPolicy(), clock);
        g.load();

        assertEquals(3, g.profile().getStreak());
        assertEquals(3, g.profile().getLongestStreak());
        assertEquals(0, g.profile().getXp());                      // history earns no XP
        assertTrue(seen.isEmpty());                                // and announces nothing

        g.rollDay();
        g.flush();
        // a second start reads game.json and does not replay (it would double count nothing, but must not reset either)
        GameService again = new GameService(new JsonRepository<>(path, GameStateDto.class),
                fresh.finance, fresh.categories, fresh.schema, new GameEventBus(), new XpPolicy(), clock);
        again.load();
        assertEquals(3, again.profile().getStreak());
    }

    @Test
    void resetDoesNotReplayHistoryAgain() throws Exception {
        add(food, 1_000);
        game.reset();
        game.flush();
        assertEquals(0, game.profile().getStreak());               // reset means a fresh start, not a rebuild
        GameService again = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx.finance, fx.categories, fx.schema, new GameEventBus(), new XpPolicy(), clock);
        again.load();
        assertEquals(0, again.profile().getStreak());
    }
}
