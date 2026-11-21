package com.okto.jalokduitnow.reminder;

import com.okto.jalokduitnow.MovableClock;
import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.model.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static com.okto.jalokduitnow.reminder.ReminderTestData.*;
import static org.junit.jupiter.api.Assertions.*;

/** Each Reminder subclass: when it fires and when it does not. All with fixed clocks. */
class RemindersTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);

    private static MovableClock at(LocalDate day, int hour, int minute) {
        return new MovableClock(day.atTime(hour, minute), TestClock.ZONE);
    }

    private static final String[] SHAMING = {"gagal", "malas", "buruk", "kecewa", "bodoh", "sayang sekali", "lalai"};

    private static void assertGentle(String text) {
        for (String bad : SHAMING) assertFalse(text.toLowerCase().contains(bad), "tone: '" + bad + "' in: " + text);
        for (char c : text.toCharArray()) assertTrue(c >= 32 && c < 127, "plain ASCII text only: " + text);
    }

    // =========================================================================
    // DailyLogReminder
    // =========================================================================

    @Test
    void dailyLogFiresFromItsTimeWhenNothingWasLoggedToday() {
        var r = new DailyLogReminder(true, LocalTime.of(20, 0));
        ReminderContext ctx = empty(DAY);
        assertFalse(r.shouldFire(ctx, at(DAY, 19, 59)));
        assertTrue(r.shouldFire(ctx, at(DAY, 20, 0)));
        assertTrue(r.shouldFire(ctx, at(DAY, 23, 59)), "still due later in the day (this is what makes a missed reminder fire)");
    }

    @Test
    void dailyLogStaysQuietOnceSomethingIsLoggedToday() throws Exception {
        var r = new DailyLogReminder(true, LocalTime.of(20, 0));
        var tx = List.of(createdAt(DAY.atTime(9, 0), DAY, UUID.randomUUID(), 1_000));
        assertFalse(r.shouldFire(context(DAY, tx, 0, 0, null, List.of(), List.of()), at(DAY, 21, 0)));
    }

    @Test
    void anEntryFromYesterdayDoesNotCountAsToday() throws Exception {
        var r = new DailyLogReminder(true, LocalTime.of(20, 0));
        var tx = List.of(createdAt(DAY.minusDays(1).atTime(21, 0), DAY.minusDays(1), UUID.randomUUID(), 1_000));
        assertTrue(r.shouldFire(context(DAY, tx, 1, 0, DAY.minusDays(1), List.of(), List.of()), at(DAY, 20, 30)));
    }

    @Test
    void theEntryDateIsWhenItWasCreatedNotWhichDayItIsFor() throws Exception {
        // an entry written today for last week still means "I logged today"
        var r = new DailyLogReminder(true, LocalTime.of(20, 0));
        var tx = List.of(createdAt(DAY.atTime(10, 0), DAY.minusDays(7), UUID.randomUUID(), 1_000));
        assertFalse(r.shouldFire(context(DAY, tx, 0, 0, null, List.of(), List.of()), at(DAY, 21, 0)));
    }

    @Test
    void theProfilesLastActiveDayAlsoCountsAsLoggedToday() {
        var r = new DailyLogReminder(true, LocalTime.of(20, 0));
        // the entry was added and later deleted: it still counted for the streak
        assertFalse(r.shouldFire(context(DAY, List.of(), 1, 0, DAY, List.of(), List.of()), at(DAY, 21, 0)));
    }

    @Test
    void aDisabledDailyLogNeverFires() {
        assertFalse(new DailyLogReminder(false, LocalTime.of(20, 0)).shouldFire(empty(DAY), at(DAY, 22, 0)));
    }

    @Test
    void dailyLogUsesTheClockZoneForTheEntryDay() throws Exception {
        // created 2026-11-21 23:30 in Jakarta; for someone in UTC the same moment is still the 21st at 16:30
        var r = new DailyLogReminder(true, LocalTime.of(10, 0));
        var tx = List.of(createdAt(DAY.atTime(23, 30), DAY, UUID.randomUUID(), 1_000));
        var clock = new MovableClock(LocalDateTime.of(2026, 11, 22, 11, 0), TestClock.ZONE);   // next morning in Jakarta
        assertTrue(r.shouldFire(context(LocalDate.of(2026, 11, 22), tx, 0, 0, null, List.of(), List.of()), clock),
                "the entry belongs to the 21st, so the 22nd has none yet");
    }

    @Test
    void dailyLogMessageActionAndPeriod() {
        var r = new DailyLogReminder(true, LocalTime.of(20, 0));
        assertEquals("daily-log", r.getId());
        assertEquals(NotificationAction.ADD_TRANSACTION, r.action());
        assertEquals("2026-11-21", r.periodKey(at(DAY, 20, 0)));
        assertTrue(r.buildMessage(empty(DAY)).toLowerCase().contains("catat"));
        assertGentle(r.buildMessage(empty(DAY)));
    }

    // =========================================================================
    // StreakRiskReminder
    // =========================================================================

    @Test
    void streakRiskNeedsARunningStreakAndNoEntryToday() {
        var r = new StreakRiskReminder(true, LocalTime.of(21, 0));
        ReminderContext running = context(DAY, List.of(), 5, 0, DAY.minusDays(1), List.of(), List.of());
        assertFalse(r.shouldFire(running, at(DAY, 20, 59)), "only in the evening");
        assertTrue(r.shouldFire(running, at(DAY, 21, 0)));
        assertFalse(r.shouldFire(empty(DAY), at(DAY, 22, 0)), "no streak, nothing to protect");
    }

    @Test
    void streakRiskStaysQuietWhenTodayIsAlreadyLogged() throws Exception {
        var r = new StreakRiskReminder(true, LocalTime.of(21, 0));
        var tx = List.of(createdAt(DAY.atTime(8, 0), DAY, UUID.randomUUID(), 1_000));
        assertFalse(r.shouldFire(context(DAY, tx, 5, 0, DAY, List.of(), List.of()), at(DAY, 22, 0)));
        assertFalse(new StreakRiskReminder(false, LocalTime.of(21, 0))
                .shouldFire(context(DAY, List.of(), 5, 0, DAY.minusDays(1), List.of(), List.of()), at(DAY, 22, 0)));
    }

    @Test
    void streakRiskMentionsTheFreezeTokenOnlyWhenOneIsAvailable() {
        var r = new StreakRiskReminder(true, LocalTime.of(21, 0));
        String without = r.buildMessage(context(DAY, List.of(), 5, 0, DAY.minusDays(1), List.of(), List.of()));
        String with = r.buildMessage(context(DAY, List.of(), 5, 2, DAY.minusDays(1), List.of(), List.of()));
        assertTrue(without.contains("5 hari"));
        assertFalse(without.toLowerCase().contains("freeze"));
        assertTrue(with.contains("2 freeze token"));
        assertGentle(without);
        assertGentle(with);
        assertEquals(NotificationAction.ADD_TRANSACTION, r.action());
        assertEquals("streak-risk", r.getId());
    }

    // =========================================================================
    // MonthlyDonorReportReminder
    // =========================================================================

    @Test
    void donorReportFiresOnTheChosenDayFromItsTime() throws Exception {
        var r = new MonthlyDonorReportReminder(true, 15, LocalTime.of(9, 0));
        ReminderContext ctx = context(LocalDate.of(2026, 11, 15), List.of(), 0, 0, null, List.of(), List.of(donor("Pak Budi")));
        LocalDate fifteenth = LocalDate.of(2026, 11, 15);
        assertFalse(r.shouldFire(ctx, at(fifteenth, 8, 59)));
        assertTrue(r.shouldFire(ctx, at(fifteenth, 9, 0)));
        assertFalse(r.shouldFire(ctx, at(fifteenth.minusDays(1), 12, 0)), "the day before");
        assertFalse(r.shouldFire(ctx, at(fifteenth.plusDays(1), 12, 0)), "the day after: a reminder from an earlier day is not replayed");
    }

    @Test
    void donorReportNeedsARecipientAndBeingEnabled() throws Exception {
        LocalDate d = LocalDate.of(2026, 11, 15);
        var r = new MonthlyDonorReportReminder(true, 15, LocalTime.of(9, 0));
        assertFalse(r.shouldFire(empty(d), at(d, 10, 0)), "no donor to send to");
        assertFalse(new MonthlyDonorReportReminder(false, 15, LocalTime.of(9, 0))
                .shouldFire(context(d, List.of(), 0, 0, null, List.of(), List.of(donor("A"))), at(d, 10, 0)));
    }

    @Test
    void day31FallsBackToTheLastDayOfShorterMonths() throws Exception {
        var r = new MonthlyDonorReportReminder(true, 31, LocalTime.of(9, 0));
        assertEquals(31, r.effectiveDay(YearMonth.of(2026, 12)));
        assertEquals(30, r.effectiveDay(YearMonth.of(2026, 4)));
        assertEquals(30, r.effectiveDay(YearMonth.of(2026, 11)));
        assertEquals(28, r.effectiveDay(YearMonth.of(2027, 2)));
        assertEquals(29, r.effectiveDay(YearMonth.of(2028, 2)), "leap year");
        assertEquals(29, new MonthlyDonorReportReminder(true, 29, LocalTime.of(9, 0)).effectiveDay(YearMonth.of(2028, 2)));
        assertEquals(28, new MonthlyDonorReportReminder(true, 29, LocalTime.of(9, 0)).effectiveDay(YearMonth.of(2027, 2)));
        assertEquals(1, new MonthlyDonorReportReminder(true, 1, LocalTime.of(9, 0)).effectiveDay(YearMonth.of(2027, 2)));
    }

    @Test
    void day31InFebruaryFiresOnThe28thOrThe29thAndNotAtAnyOtherTime() throws Exception {
        var r = new MonthlyDonorReportReminder(true, 31, LocalTime.of(9, 0));
        List<Recipient> who = List.of(donor("A"));
        LocalDate feb27 = LocalDate.of(2027, 2, 27), feb28 = LocalDate.of(2027, 2, 28), mar1 = LocalDate.of(2027, 3, 1);
        assertFalse(r.shouldFire(context(feb27, List.of(), 0, 0, null, List.of(), who), at(feb27, 10, 0)));
        assertTrue(r.shouldFire(context(feb28, List.of(), 0, 0, null, List.of(), who), at(feb28, 10, 0)));
        assertFalse(r.shouldFire(context(mar1, List.of(), 0, 0, null, List.of(), who), at(mar1, 10, 0)));

        LocalDate leap28 = LocalDate.of(2028, 2, 28), leap29 = LocalDate.of(2028, 2, 29);
        assertFalse(r.shouldFire(context(leap28, List.of(), 0, 0, null, List.of(), who), at(leap28, 10, 0)));
        assertTrue(r.shouldFire(context(leap29, List.of(), 0, 0, null, List.of(), who), at(leap29, 10, 0)));
    }

    @Test
    void donorReportAcrossTheYearBoundary() throws Exception {
        var r = new MonthlyDonorReportReminder(true, 31, LocalTime.of(9, 0));
        List<Recipient> who = List.of(donor("A"));
        LocalDate dec31 = LocalDate.of(2026, 12, 31), jan1 = LocalDate.of(2027, 1, 1), jan31 = LocalDate.of(2027, 1, 31);
        assertTrue(r.shouldFire(context(dec31, List.of(), 0, 0, null, List.of(), who), at(dec31, 10, 0)));
        assertFalse(r.shouldFire(context(jan1, List.of(), 0, 0, null, List.of(), who), at(jan1, 10, 0)));
        assertTrue(r.shouldFire(context(jan31, List.of(), 0, 0, null, List.of(), who), at(jan31, 10, 0)));
        // each month is its own period, across the New Year too
        assertEquals("2026-12", r.periodKey(at(dec31, 10, 0)));
        assertEquals("2027-01", r.periodKey(at(jan31, 10, 0)));
        assertNotEquals(r.periodKey(at(dec31, 10, 0)), r.periodKey(at(jan1, 10, 0)));
    }

    @Test
    void donorReportMessageNamesTheDefaultRecipientAndOpensTheReport() throws Exception {
        var r = new MonthlyDonorReportReminder(true, 1, LocalTime.of(9, 0));
        ReminderContext ctx = context(DAY, List.of(), 0, 0, null, List.of(), List.of(donor("Pak Budi")));
        assertTrue(r.buildMessage(ctx).contains("Pak Budi"));
        assertEquals(NotificationAction.OPEN_REPORT, r.action());
        assertGentle(r.buildMessage(ctx));
        assertEquals("Hari ini waktunya menyiapkan laporan donor. Buka halaman Laporan untuk meninjau dan mengirimnya.",
                r.buildMessage(empty(DAY)), "no name when there is no default recipient");
    }

    // =========================================================================
    // BudgetThresholdReminder
    // =========================================================================

    private ReminderContext spent(LocalDate today, Category c, long amount) throws Exception {
        Transaction t = new Expense(today, c.getId(), amount);
        return context(today, List.of(t), 0, 0, null, List.of(c), List.of());
    }

    @Test
    void theWarningFiresFromEightyPercentUpToTheBudget() throws Exception {
        Category food = budgeted("Makan", 1_000_000);
        var warn = new BudgetThresholdReminder(true, food, 80, true);
        var clock = at(DAY, 12, 0);
        assertFalse(warn.shouldFire(spent(DAY, food, 799_999), clock));
        assertTrue(warn.shouldFire(spent(DAY, food, 800_000), clock), "exactly 80%");
        assertTrue(warn.shouldFire(spent(DAY, food, 999_999), clock));
        assertFalse(warn.shouldFire(spent(DAY, food, 1_000_000), clock), "at the budget the 'reached' message takes over");
        assertFalse(warn.shouldFire(spent(DAY, food, 1_500_000), clock));
    }

    @Test
    void theReachedMessageFiresAtAndAboveTheBudget() throws Exception {
        Category food = budgeted("Makan", 1_000_000);
        var reached = new BudgetThresholdReminder(true, food, 100, false);
        var clock = at(DAY, 12, 0);
        assertFalse(reached.shouldFire(spent(DAY, food, 999_999), clock));
        assertTrue(reached.shouldFire(spent(DAY, food, 1_000_000), clock));
        assertTrue(reached.shouldFire(spent(DAY, food, 1_500_000), clock));
    }

    @Test
    void onlyThisCategorysExpensesOfThisMonthCount() throws Exception {
        Category food = budgeted("Makan", 1_000_000);
        UUID other = UUID.randomUUID();
        var warn = new BudgetThresholdReminder(true, food, 80, true);
        List<Transaction> tx = List.of(
                new Expense(DAY.minusMonths(1), food.getId(), 900_000),      // last month
                new Expense(DAY, other, 900_000),                            // another category
                new Income(DAY, food.getId(), 900_000),                      // income is not spending
                new Expense(DAY, food.getId(), 100_000));
        assertFalse(warn.shouldFire(context(DAY, tx, 0, 0, null, List.of(food), List.of()), at(DAY, 12, 0)));
    }

    @Test
    void aNewMonthStartsFromZeroAndHasItsOwnPeriod() throws Exception {
        Category food = budgeted("Makan", 1_000_000);
        var warn = new BudgetThresholdReminder(true, food, 80, true);
        LocalDate dec = LocalDate.of(2026, 12, 31), jan = LocalDate.of(2027, 1, 1);
        Transaction december = new Expense(dec, food.getId(), 900_000);
        assertTrue(warn.shouldFire(context(dec, List.of(december), 0, 0, null, List.of(food), List.of()), at(dec, 12, 0)));
        assertFalse(warn.shouldFire(context(jan, List.of(december), 0, 0, null, List.of(food), List.of()), at(jan, 12, 0)),
                "December's spending does not count in January");
        assertEquals("2026-12", warn.periodKey(at(dec, 12, 0)));
        assertEquals("2027-01", warn.periodKey(at(jan, 12, 0)));
    }

    @Test
    void budgetReminderIdsAreUniquePerCategoryAndThreshold() throws Exception {
        Category a = budgeted("A", 100_000), b = budgeted("B", 100_000);
        var ids = List.of(new BudgetThresholdReminder(true, a, 80, true).getId(), new BudgetThresholdReminder(true, a, 100, false).getId(),
                new BudgetThresholdReminder(true, b, 80, true).getId(), new BudgetThresholdReminder(true, b, 100, false).getId());
        assertEquals(4, ids.stream().distinct().count());
    }

    @Test
    void budgetMessagesMentionTheFiguresAndStayEncouraging() throws Exception {
        Category food = budgeted("Makan", 1_000_000);
        ReminderContext ctx = spent(DAY, food, 850_000);
        String warn = new BudgetThresholdReminder(true, food, 80, true).buildMessage(ctx);
        assertTrue(warn.contains("Makan") && warn.contains("80%") && warn.contains("Rp 850.000") && warn.contains("Rp 1.000.000"), warn);
        String reached = new BudgetThresholdReminder(true, food, 100, false).buildMessage(spent(DAY, food, 1_100_000));
        assertTrue(reached.contains("mencapai budget") && reached.contains("Rp 1.100.000"), reached);
        assertGentle(warn);
        assertGentle(reached);
        assertEquals(NotificationAction.OPEN_CATEGORIES, new BudgetThresholdReminder(true, food, 80, true).action());
        assertFalse(new BudgetThresholdReminder(false, food, 80, true).shouldFire(spent(DAY, food, 950_000), at(DAY, 12, 0)));
    }

    // =========================================================================
    // the factory
    // =========================================================================

    @Test
    void theFactoryBuildsTheFixedRemindersAndAPairPerBudgetedExpenseCategory() throws Exception {
        Category withBudget = budgeted("Makan", 500_000);
        Category noBudget = Category.create("Lain", CategoryType.EXPENSE, "other", "#112233", null);
        Category archived = budgeted("Lama", 100_000);
        archived.setArchived(true);
        ReminderContext ctx = context(DAY, List.of(), 0, 0, null, List.of(withBudget, noBudget, archived), List.of());

        List<Reminder> built = ReminderFactory.build(new ReminderSettings(), ctx);
        List<String> ids = built.stream().map(Reminder::getId).toList();
        assertTrue(ids.containsAll(List.of("daily-log", "streak-risk", "donor-report")));
        assertEquals(2, ids.stream().filter(i -> i.startsWith("budget:")).count());
        assertTrue(ids.contains("budget:" + withBudget.getId() + ":80"));
        assertTrue(ids.contains("budget:" + withBudget.getId() + ":100"));
        assertEquals(5, built.size());
    }

    @Test
    void theFactoryAppliesTheSettingsAndTheWarnPercent() throws Exception {
        ReminderSettings s = new ReminderSettings();
        s.setEnabled(ReminderKind.DAILY_LOG, false);
        s.setEnabled(ReminderKind.DONOR_REPORT, true);
        s.setBudgetWarnPercent(70);
        s.setDailyLogTime(LocalTime.of(18, 30));
        Category c = budgeted("Makan", 500_000);
        List<Reminder> built = ReminderFactory.build(s, context(DAY, List.of(), 0, 0, null, List.of(c), List.of()));

        assertFalse(built.stream().filter(r -> r.getId().equals("daily-log")).findFirst().orElseThrow().isEnabled());
        assertTrue(built.stream().filter(r -> r.getId().equals("donor-report")).findFirst().orElseThrow().isEnabled());
        assertTrue(built.stream().anyMatch(r -> r.getId().equals("budget:" + c.getId() + ":70")));
        assertFalse(built.stream().anyMatch(r -> r.getId().equals("budget:" + c.getId() + ":80")));
    }

    @Test
    void theContextIsAReadOnlySnapshot() throws Exception {
        List<Transaction> tx = new java.util.ArrayList<>(List.of(new Expense(DAY, UUID.randomUUID(), 1_000)));
        ReminderContext ctx = context(DAY, tx, 1, 0, null, List.of(), List.of());
        tx.clear();                                                    // changing the original list does not change the snapshot
        assertEquals(1, ctx.transactions().size());
        assertThrows(UnsupportedOperationException.class, () -> ctx.transactions().add(null));
        assertThrows(UnsupportedOperationException.class, () -> ctx.recipients().add(null));
    }
}
