package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.achievement.Achievement;
import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.quest.*;
import com.okto.jalokduitnow.report.DonorReportGenerator;
import com.okto.jalokduitnow.report.PersonalReportGenerator;
import com.okto.jalokduitnow.report.ReportOptions;
import com.okto.jalokduitnow.storage.CsvExporter.Delimiter;
import com.okto.jalokduitnow.storage.DonorSettingsDto;
import com.okto.jalokduitnow.storage.GameStateDto;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.RecipientDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nothing in the app may depend on "the year": data from other years, adding a transaction in any valid year, and the
 * game around December 31 to January 1. All with a fixed clock; the years below are test data, not limits.
 */
class YearBoundaryTest {
    @TempDir Path dir;
    TestClock clock;
    Fixture fx;
    GameEventBus bus;
    GameService game;
    BackupService backup;
    DonorSettings donors;
    List<GameEvent> events;
    UUID food, savings, salary;

    @BeforeEach
    void setUp() throws Exception {
        clock = new TestClock(LocalDate.of(2026, 12, 31));
        fx = Fixture.in(dir, clock);
        bus = new GameEventBus();
        fx.finance.setEventBus(bus);
        game = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx.finance, fx.categories, fx.schema, bus, new XpPolicy(), clock);
        game.load();
        game.rollDay();
        donors = new DonorSettings(new JsonRepository<>(dir.resolve("donor.json"), DonorSettingsDto.class));
        var recipients = new RecipientService(new JsonRepository<>(dir.resolve("recipients.json"), RecipientDto.class));
        backup = new BackupService(fx.finance, fx.schema, fx.categories, game, recipients, donors, clock);
        events = new ArrayList<>();
        bus.subscribe(GameEvent.class, events::add);
        food = fx.expense("Makan");
        savings = fx.expense("Tabungan");
        salary = fx.income("Gaji");
    }

    private Transaction spend(LocalDate date, UUID category, long amount) throws Exception {
        Transaction t = new Expense(date, category, amount);
        fx.finance.add(t);
        return t;
    }

    private <E extends GameEvent> List<E> eventsOf(Class<E> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private void today(int y, int m, int d) { clock.set(LocalDate.of(y, m, d)); }

    // =========================================================================
    // data from any year: totals, reports, exports
    // =========================================================================

    @Test
    void dataInAnyYearAppearsInTheRightMonthAndInTheTotals() throws Exception {
        spend(LocalDate.of(2024, 6, 15), food, 10_000);
        spend(LocalDate.of(2026, 12, 31), food, 20_000);
        spend(LocalDate.of(2027, 1, 1), food, 30_000);
        fx.finance.add(new Income(LocalDate.of(2027, 3, 10), salary, 500_000));

        assertEquals(List.of(YearMonth.of(2024, 6), YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 3)),
                List.copyOf(fx.finance.summaryByMonth().keySet()));
        assertEquals(30_000, fx.finance.summaryFor(YearMonth.of(2027, 1)).expense());
        assertEquals(20_000, fx.finance.summaryFor(YearMonth.of(2026, 12)).expense());   // 31 Dec stays in December
        assertEquals(500_000, fx.finance.summaryFor(YearMonth.of(2027, 3)).income());
        assertEquals(60_000, fx.finance.totalExpense());
        assertEquals(500_000 - 60_000, fx.finance.balance());
        assertEquals(30_000, fx.finance.expenseByCategory(YearMonth.of(2027, 1)).get(food));
        assertTrue(fx.finance.expenseByCategory(YearMonth.of(2025, 6)).isEmpty());          // a year with no data is simply empty
    }

    @Test
    void aDateBeforeTheEarliestEntryWorksAndExtendsThePickers() throws Exception {
        spend(LocalDate.of(2026, 5, 1), food, 5_000);
        var before = spend(LocalDate.of(2023, 1, 5), food, 7_000);                           // earlier than anything so far

        assertEquals(7_000, fx.finance.summaryFor(YearMonth.of(2023, 1)).expense());
        assertEquals(before.getId(), fx.finance.getAll().get(fx.finance.getAll().size() - 1).getId(), "sorted newest first");
        assertEquals(12_000, fx.finance.totalExpense());
        List<Integer> years = PeriodRange.years(fx.finance.summaryByMonth().keySet(), null, clock);
        assertEquals(List.of(2023, 2024, 2025, 2026, 2027), years);
        assertEquals(YearMonth.of(2026, 5), PeriodRange.defaultMonth(fx.finance.summaryByMonth().keySet(), clock));   // current month (Dec 2026) is empty
    }

    @Test
    void aTransactionInANewYearIsReportedAndExported() throws Exception {
        spend(LocalDate.of(2026, 12, 31), food, 20_000);
        spend(LocalDate.of(2027, 1, 1), food, 30_000);
        spend(LocalDate.of(2027, 1, 20), food, 5_000);

        String jan = new PersonalReportGenerator(fx.finance, fx.categories, fx.schema, clock, ReportOptions.defaults())
                .generate(YearMonth.of(2027, 1));
        assertTrue(jan.contains("LAPORAN KEUANGAN - Januari 2027"), jan);
        assertTrue(jan.contains("Pengeluaran: Rp 35.000"));
        assertFalse(jan.contains("55.000"), "December is not part of January");

        String dec = new DonorReportGenerator(fx.finance, fx.categories, fx.schema, donors, "X", clock, ReportOptions.defaults())
                .generate(YearMonth.of(2026, 12));
        assertTrue(dec.contains("Total pengeluaran: Rp 20.000"));

        String csvJan = backup.transactionsCsv(YearMonth.of(2027, 1), Delimiter.COMMA);
        assertEquals(3, csvJan.split("\r\n").length);                                         // header + 2
        assertTrue(csvJan.contains("2027-01-01,Expense,Makan,30000"));
        assertFalse(csvJan.contains("2026-12-31"));
        String csvAll = backup.transactionsCsv(null, Delimiter.COMMA);
        assertTrue(csvAll.indexOf("2026-12-31") < csvAll.indexOf("2027-01-01"), "oldest first across the year");
        assertEquals(3, backup.snapshot(false).transactions().size());
    }

    @Test
    void theLastSixMonthsChartDataIsRightAcrossNewYear() throws Exception {
        spend(LocalDate.of(2026, 9, 10), food, 1_000);
        spend(LocalDate.of(2026, 12, 31), food, 4_000);
        spend(LocalDate.of(2027, 1, 1), food, 5_000);
        spend(LocalDate.of(2027, 2, 14), food, 6_000);

        List<Long> expenses = PeriodRange.lastMonths(YearMonth.of(2027, 2), 6).stream()
                .map(m -> fx.finance.summaryFor(m).expense()).toList();
        assertEquals(List.of(1_000L, 0L, 0L, 4_000L, 5_000L, 6_000L), expenses);              // Sep, Oct, Nov, Dec, Jan, Feb
    }

    @Test
    void theMonthlyGradeWorksInJanuaryForDecemberData() throws Exception {
        fx.finance.add(new Income(LocalDate.of(2026, 12, 1), salary, 1_000_000));
        spend(LocalDate.of(2026, 12, 20), food, 600_000);
        assertEquals(MonthlyScore.Grade.A, game.scoreFor(YearMonth.of(2026, 12)).grade());
        assertEquals(MonthlyScore.Grade.NONE, game.scoreFor(YearMonth.of(2027, 1)).grade());
    }

    // =========================================================================
    // streak across December 31 -> January 1
    // =========================================================================

    @Test
    void entriesJustBeforeAndJustAfterMidnightAreTwoDaysOfStreak() throws Exception {
        clock.set(LocalDate.of(2026, 12, 31), LocalTime.of(23, 59, 30));
        spend(LocalDate.of(2026, 12, 31), food, 1_000);
        assertEquals(1, game.profile().getStreak());

        clock.set(LocalDate.of(2027, 1, 1), LocalTime.of(0, 0, 30));
        spend(LocalDate.of(2027, 1, 1), food, 1_000);

        assertEquals(2, game.profile().getStreak());
        assertEquals(2, game.profile().getLongestStreak());
        assertEquals(LocalDate.of(2027, 1, 1), game.profile().getLastActiveDate());
        assertTrue(eventsOf(GameEvent.StreakChanged.class).stream().noneMatch(e -> e.change().broken()));
    }

    @Test
    void theMidnightRollOnNewYearsDayKeepsAStreakThatIsStillAlive() throws Exception {
        spend(LocalDate.of(2026, 12, 31), food, 1_000);
        today(2027, 1, 1);
        game.rollDay();                                                    // the scheduled check at midnight
        assertEquals(1, game.profile().getStreak());
        assertEquals(0, eventsOf(GameEvent.StreakChanged.class).stream().filter(e -> e.change().broken()).count());
        spend(LocalDate.of(2027, 1, 1), food, 1_000);
        assertEquals(2, game.profile().getStreak());
    }

    @Test
    void aMissedNewYearsDayIsSavedByTheFreezeToken() throws Exception {
        today(2026, 12, 30);
        spend(LocalDate.of(2026, 12, 30), food, 1_000);
        today(2026, 12, 31);
        spend(LocalDate.of(2026, 12, 31), food, 1_000);
        assertEquals(2, game.profile().getStreak());
        assertEquals(1, game.profile().getFreezeTokens());                 // granted for the week starting Monday 28 Dec

        today(2027, 1, 2);                                                 // 1 January was missed
        spend(LocalDate.of(2027, 1, 2), food, 1_000);
        assertEquals(3, game.profile().getStreak());                       // not reset: the token covered the gap
        assertEquals(0, game.profile().getFreezeTokens());
        assertTrue(eventsOf(GameEvent.StreakChanged.class).stream().anyMatch(e -> e.change().freezeTokensUsed() == 1));
    }

    @Test
    void aLongRunOfDailyEntriesNeverResetsAtNewYear() throws Exception {
        LocalDate day = LocalDate.of(2026, 12, 25);
        for (int i = 0; i < 15; i++) {                                     // 25 Dec .. 8 Jan
            clock.set(day.plusDays(i));
            spend(day.plusDays(i), food, 1_000);
        }
        assertEquals(15, game.profile().getStreak());
        assertEquals(15, game.profile().getLongestStreak());
        assertTrue(eventsOf(GameEvent.StreakChanged.class).stream().noneMatch(e -> e.change().broken()));
    }

    @Test
    void weeklyTokensAreGrantedOnMondaysAcrossTheYearEnd() {
        GameProfile p = new GameProfile();
        p.rollTo(LocalDate.of(2026, 12, 28));                              // Monday
        assertEquals(1, p.getFreezeTokens());
        p.rollTo(LocalDate.of(2027, 1, 3));                                // Sunday of the same week: nothing new
        assertEquals(1, p.getFreezeTokens());
        p.rollTo(LocalDate.of(2027, 1, 4));                                // the next Monday, in the new year
        assertEquals(2, p.getFreezeTokens());
    }

    @Test
    void theSchedulerWaitsTheRightTimeAtTheYearEnd() {
        clock.set(LocalDate.of(2026, 12, 31), LocalTime.of(23, 59, 30));
        assertEquals(java.time.Duration.ofSeconds(31), com.okto.jalokduitnow.thread.DailyScheduler.untilNextMidnight(clock));
    }

    // =========================================================================
    // quests across the year end
    // =========================================================================

    @Test
    void theWeekAcrossNewYearIsOneQuestPeriod() {
        QuestPeriod week = QuestPeriod.weekOf(LocalDate.of(2027, 1, 1));
        assertEquals(LocalDate.of(2026, 12, 28), week.start());
        assertEquals(LocalDate.of(2027, 1, 3), week.end());
        assertEquals(7, week.days());
        assertTrue(week.contains(LocalDate.of(2026, 12, 31)));
        assertTrue(week.contains(LocalDate.of(2027, 1, 1)));
        assertFalse(week.contains(LocalDate.of(2027, 1, 4)));
        assertTrue(week.isOver(LocalDate.of(2027, 1, 4)));
        assertEquals(week, QuestPeriod.weekOf(LocalDate.of(2026, 12, 29)));
    }

    @Test
    void aLogDaysQuestCountsDaysOnBothSidesOfNewYearAndPaysOnce() throws Exception {
        today(2026, 12, 29);
        game.rollDay();
        LogDaysQuest log = (LogDaysQuest) game.quests().stream().filter(q -> q instanceof LogDaysQuest).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 12, 28), log.getPeriod().start());
        assertEquals(LocalDate.of(2027, 1, 3), log.getPeriod().end());

        LocalDate[] days = {LocalDate.of(2026, 12, 29), LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)};
        for (LocalDate d : days) {
            clock.set(d);
            spend(d, food, 1_000);
        }
        assertEquals(5, game.progressOf(log).current());
        assertTrue(game.progressOf(log).isCompleted());
        assertEquals(1, eventsOf(GameEvent.QuestCompleted.class).stream().filter(e -> e.quest().getId().equals(log.getId())).count());

        today(2027, 1, 4);                                                 // Monday: a new week, nothing paid twice
        game.rollDay();
        assertEquals(1, eventsOf(GameEvent.QuestCompleted.class).stream().filter(e -> e.quest().getId().equals(log.getId())).count());
        assertEquals(LocalDate.of(2027, 1, 4), game.quests().get(0).getPeriod().start());
        assertTrue(game.quests().stream().allMatch(q -> q.getId().contains("2027-01-04")), "new ids for the new week");
    }

    @Test
    void aBudgetQuestAddsUpSpendingFromBothYearsAndIsJudgedWhenTheWeekEnds() throws Exception {
        fx.categories.setBudget(food, 210_000L);                           // weekly limit 49,000
        today(2026, 12, 28);
        game.reset();
        CategoryBudgetQuest budget = (CategoryBudgetQuest) game.quests().stream()
                .filter(q -> q instanceof CategoryBudgetQuest).findFirst().orElseThrow();
        assertEquals(49_000, budget.getLimit());

        today(2026, 12, 31);
        spend(LocalDate.of(2026, 12, 31), food, 25_000);
        today(2027, 1, 2);
        spend(LocalDate.of(2027, 1, 2), food, 20_000);                     // 45,000 in total, over the New Year, under 49,000
        assertEquals(45_000, game.progressOf(budget).current());
        assertEquals(QuestStatus.IN_PROGRESS, game.progressOf(budget).status());

        today(2027, 1, 4);
        game.rollDay();
        assertTrue(game.isRewarded(budget));
    }

    @Test
    void anOverspentBudgetAcrossTheYearEndFails() throws Exception {
        fx.categories.setBudget(food, 210_000L);
        today(2026, 12, 28);
        game.reset();
        CategoryBudgetQuest budget = (CategoryBudgetQuest) game.quests().stream()
                .filter(q -> q instanceof CategoryBudgetQuest).findFirst().orElseThrow();
        spend(LocalDate.of(2026, 12, 31), food, 30_000);
        spend(LocalDate.of(2027, 1, 1), food, 30_000);                     // 60,000 > 49,000 only when both years are added
        assertEquals(QuestStatus.FAILED, game.progressOf(budget).status());
        today(2027, 1, 4);
        game.rollDay();
        assertFalse(game.isRewarded(budget));
    }

    @Test
    void aNoSpendQuestCountsFreeDaysAcrossTheBoundary() throws Exception {
        NoSpendDaysQuest quest = new NoSpendDaysQuest("n", "t", QuestPeriod.weekOf(LocalDate.of(2027, 1, 1)), 50, 2);
        List<Transaction> txs = List.of(new Expense(LocalDate.of(2026, 12, 28), food, 1_000));   // spent only on Monday
        clock.set(LocalDate.of(2027, 1, 2));                                // Saturday: Tue 29, Wed 30, Thu 31, Fri 1 are finished and free
        QuestProgress p = quest.evaluate(txs, clock);
        assertEquals(4, p.current());
        assertEquals(QuestStatus.COMPLETED, p.status());
    }

    @Test
    void theNextWeeksQuestsLookBackAcrossTheYearForTheirLimits() throws Exception {
        // spending in the 4 weeks before 4 Jan 2027 sits in December 2026
        for (int i = 0; i < 4; i++) spend(LocalDate.of(2026, 12, 7).plusWeeks(i), food, 100_000);
        today(2027, 1, 4);
        game.rollDay();
        CategoryBudgetQuest q = (CategoryBudgetQuest) game.quests().stream()
                .filter(x -> x instanceof CategoryBudgetQuest).findFirst().orElseThrow();
        assertEquals(food, q.getCategoryId());
        assertEquals(90_000, q.getLimit());                                 // 90% of the 100,000 weekly average
    }

    // =========================================================================
    // savings goals and badges across the year end
    // =========================================================================

    @Test
    void aSavingsGoalAddsUpDepositsFromBothYears() throws Exception {
        SavingsGoal goal = game.addGoal("Laptop", 100_000, savings);
        spend(LocalDate.of(2026, 12, 30), savings, 40_000);
        assertEquals(40, game.goalProgress(goal).percent());
        spend(LocalDate.of(2027, 1, 2), savings, 60_000);
        assertEquals(100, game.goalProgress(goal).percent());
        assertEquals(100_000, game.goalProgress(goal).saved());

        Achievement full = game.achievements().stream().filter(a -> a.getId().equals("savings-100")).findFirst().orElseThrow();
        assertTrue(game.unlockedOn(full).isPresent(), "the 100% badge is not per-year");
    }

    @Test
    void theBudgetBadgeInJanuaryLooksAtDecemberOfTheYearBefore() throws Exception {
        fx.categories.setBudget(food, 500_000L);
        spend(LocalDate.of(2026, 12, 15), food, 400_000);
        today(2027, 1, 15);
        game.rollDay();
        Achievement budgetBadge = game.achievements().stream().filter(a -> a.getId().equals("budget-month")).findFirst().orElseThrow();
        assertTrue(game.unlockedOn(budgetBadge).isPresent());
    }

    @Test
    void theBudgetBadgeIsNotGivenWhenDecemberWentOverBudget() throws Exception {
        fx.categories.setBudget(food, 500_000L);
        spend(LocalDate.of(2026, 12, 15), food, 600_000);
        today(2027, 1, 15);
        game.rollDay();
        Achievement budgetBadge = game.achievements().stream().filter(a -> a.getId().equals("budget-month")).findFirst().orElseThrow();
        assertTrue(game.unlockedOn(budgetBadge).isEmpty());
    }

    @Test
    void theStateSurvivesARestartAcrossTheYearEnd() throws Exception {
        spend(LocalDate.of(2026, 12, 31), food, 1_000);
        today(2027, 1, 1);
        spend(LocalDate.of(2027, 1, 1), food, 1_000);
        game.flush();
        fx.flush();

        Fixture fx2 = fx.reload();
        GameService again = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx2.finance, fx2.categories, fx2.schema, new GameEventBus(), new XpPolicy(), clock);
        again.load();
        again.rollDay();
        assertEquals(2, again.profile().getStreak());
        assertEquals(game.profile().getXp(), again.profile().getXp());
    }
}
