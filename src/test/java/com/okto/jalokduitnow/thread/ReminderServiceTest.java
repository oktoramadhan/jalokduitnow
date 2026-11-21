package com.okto.jalokduitnow.thread;

import com.okto.jalokduitnow.MovableClock;
import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.reminder.*;
import com.okto.jalokduitnow.service.NotificationCenter;
import com.okto.jalokduitnow.service.ReminderLog;
import com.okto.jalokduitnow.service.ReminderSettingsService;
import com.okto.jalokduitnow.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reminder engine with a fixed, movable clock. The UI executor is "run it right here", so a tick is just a method
 * call and the tests need no sleeping (the timer test at the end uses real milliseconds).
 */
class ReminderServiceTest {
    private static final ZoneId JAKARTA = TestClock.ZONE;

    @TempDir Path dir;
    MovableClock clock;
    ReminderSettingsService settings;
    ReminderLog log;
    NotificationCenter center;
    GameEventBus bus;
    ReminderService service;
    List<GameEvent> events;
    List<AppNotification> sunk;

    // what the reminders will "see": the tests change these
    int streak;
    int tokens;
    LocalDate lastActive;
    List<Transaction> transactions = new ArrayList<>();
    List<Category> categories = new ArrayList<>();
    List<Recipient> recipients = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        clock = new MovableClock(LocalDateTime.of(2026, 11, 21, 12, 0), JAKARTA);
        settings = new ReminderSettingsService(new JsonRepository<>(dir.resolve("reminders.json"), ReminderSettingsDto.class));
        settings.load();
        log = new ReminderLog(new JsonRepository<>(dir.resolve("log.json"), ReminderLogDto.class));
        log.load();
        center = new NotificationCenter(new JsonRepository<>(dir.resolve("notifications.json"), NotificationDto.class));
        center.load();
        bus = new GameEventBus();
        events = new ArrayList<>();
        bus.subscribe(GameEvent.class, events::add);
        service = newService(settings, log, center);
        sunk = new ArrayList<>();
        service.addSink(sunk::add);
        settings.setQuietHours(false, LocalTime.of(22, 0), LocalTime.of(7, 0));   // most tests are not about quiet hours
    }

    private ReminderService newService(ReminderSettingsService s, ReminderLog l, NotificationCenter c) {
        return new ReminderService(s, l, c, this::context, bus, clock, Runnable::run);
    }

    private ReminderContext context() {
        return new ReminderContext(LocalDate.now(clock), transactions, streak, tokens, lastActive, categories, recipients, java.util.Set.of());
    }

    private List<String> ids(List<AppNotification> list) {
        return list.stream().map(AppNotification::getReminderId).toList();
    }

    private void at(int day, int hour, int minute) {
        clock.setLocal(LocalDateTime.of(2026, 11, day, hour, minute));
    }

    // =========================================================================
    // firing, and only once per period
    // =========================================================================

    @Test
    void aDueReminderFiresAndTheNotificationIsStoredShownAndAnnounced() {
        at(21, 20, 5);
        List<AppNotification> delivered = service.tick();

        assertEquals(List.of("daily-log"), ids(delivered));
        AppNotification n = delivered.get(0);
        assertEquals("Waktunya mencatat", n.getTitle());
        assertEquals(NotificationAction.ADD_TRANSACTION, n.getAction());
        assertFalse(n.isRead());
        assertEquals(clock.instant(), n.getCreatedAt());
        assertEquals(List.of(n), center.getAll());
        assertEquals(List.of(n), sunk);
        assertEquals(1, events.stream().filter(e -> e instanceof GameEvent.ReminderFired).count());
        assertEquals("daily-log", ((GameEvent.ReminderFired) events.get(0)).reminderId());
    }

    @Test
    void nothingFiresBeforeTheReminderTime() {
        at(21, 19, 59);
        assertTrue(service.tick().isEmpty());
        assertTrue(center.getAll().isEmpty());
    }

    @Test
    void aReminderFiresOnlyOncePerDayNoMatterHowOftenItIsChecked() {
        at(21, 20, 0);
        assertEquals(1, service.tick().size());
        at(21, 20, 1);
        assertTrue(service.tick().isEmpty());
        at(21, 23, 59);
        assertTrue(service.tick().isEmpty());
        assertEquals(1, center.getAll().size());
        assertEquals(1, log.size());
    }

    @Test
    void theNextDayIsANewPeriodAndFiresAgain() {
        at(21, 20, 0);
        service.tick();
        at(22, 20, 0);
        assertEquals(List.of("daily-log"), ids(service.tick()));
        assertEquals(2, center.getAll().size());
    }

    @Test
    void theYearBoundaryIsJustAnotherDayChange() {
        clock.setLocal(LocalDateTime.of(2026, 12, 31, 20, 0));
        assertEquals(List.of("daily-log"), ids(service.tick()));
        clock.setLocal(LocalDateTime.of(2026, 12, 31, 20, 30));
        assertTrue(service.tick().isEmpty());
        clock.setLocal(LocalDateTime.of(2027, 1, 1, 20, 0));
        assertEquals(List.of("daily-log"), ids(service.tick()));
    }

    @Test
    void severalDueRemindersFireTogetherInOneTick() throws Exception {
        streak = 4;
        lastActive = LocalDate.of(2026, 11, 20);
        at(21, 21, 30);
        assertEquals(List.of("daily-log", "streak-risk"), ids(service.tick()));
        assertTrue(center.getAll().stream().anyMatch(n -> n.getMessage().contains("4 hari")));
    }

    @Test
    void remindersThatAreSwitchedOffStaySilent() throws Exception {
        settings.setEnabled(ReminderKind.DAILY_LOG, false);
        at(21, 20, 5);
        assertTrue(service.tick().isEmpty());
        settings.setEnabled(ReminderKind.DAILY_LOG, true);                        // switching it on makes it due right away
        assertEquals(List.of("daily-log"), ids(service.tick()));
    }

    @Test
    void thereIsNothingToSayWhenTodayIsAlreadyLogged() throws Exception {
        Transaction t = new Expense(LocalDate.of(2026, 11, 21), UUID.randomUUID(), 1_000);
        t.setCreatedAt(LocalDateTime.of(2026, 11, 21, 9, 0).atZone(JAKARTA).toInstant());
        transactions.add(t);
        streak = 3;
        lastActive = LocalDate.of(2026, 11, 21);
        at(21, 22, 0);
        assertTrue(service.tick().isEmpty());
    }

    // =========================================================================
    // budget thresholds: once per threshold per month
    // =========================================================================

    private Category budget(String name, long amount) throws Exception {
        Category c = Category.create(name, CategoryType.EXPENSE, "food", "#112233", amount);
        categories.add(c);
        return c;
    }

    private void spend(Category c, LocalDate date, long amount) throws Exception {
        transactions.add(new Expense(date, c.getId(), amount));
    }

    @Test
    void eachBudgetThresholdFiresOnceAMonth() throws Exception {
        Category food = budget("Makan", 1_000_000);
        at(21, 12, 0);
        lastActive = LocalDate.of(2026, 11, 21);       // logged today: the daily reminders are not part of this test
        spend(food, LocalDate.of(2026, 11, 3), 700_000);
        assertTrue(service.tick().isEmpty(), "70%: nothing yet");

        spend(food, LocalDate.of(2026, 11, 10), 150_000);          // 85%
        assertEquals(List.of("budget:" + food.getId() + ":80"), ids(service.tick()));
        assertTrue(service.tick().isEmpty(), "the 80% message does not repeat");

        spend(food, LocalDate.of(2026, 11, 12), 200_000);          // 105%
        assertEquals(List.of("budget:" + food.getId() + ":100"), ids(service.tick()));
        spend(food, LocalDate.of(2026, 11, 13), 50_000);
        assertTrue(service.tick().isEmpty(), "nor does the budget-reached message");
    }

    @Test
    void aBigExpenseThatJumpsPastTheBudgetOnlyGivesTheReachedMessage() throws Exception {
        Category food = budget("Makan", 1_000_000);
        lastActive = LocalDate.of(2026, 11, 21);
        at(21, 12, 0);
        spend(food, LocalDate.of(2026, 11, 3), 1_300_000);
        assertEquals(List.of("budget:" + food.getId() + ":100"), ids(service.tick()));
    }

    @Test
    void budgetsStartOverInANewMonthAcrossTheYearEnd() throws Exception {
        Category food = budget("Makan", 1_000_000);
        lastActive = null;
        clock.setLocal(LocalDateTime.of(2026, 12, 31, 12, 0));
        spend(food, LocalDate.of(2026, 12, 5), 900_000);
        assertEquals(1, ids(service.tick()).stream().filter(i -> i.endsWith(":80")).count());

        clock.setLocal(LocalDateTime.of(2027, 1, 1, 12, 0));
        assertTrue(ids(service.tick()).stream().noneMatch(i -> i.startsWith("budget")), "January starts at zero");
        spend(food, LocalDate.of(2027, 1, 8), 850_000);
        clock.setLocal(LocalDateTime.of(2027, 1, 9, 12, 0));
        assertEquals(1, ids(service.tick()).stream().filter(i -> i.endsWith(":80")).count(), "fires again: a new month");
    }

    // =========================================================================
    // monthly donor reminder
    // =========================================================================

    @Test
    void theMonthlyDonorReminderFiresOncePerMonthOnItsDay() throws Exception {
        settings.setEnabled(ReminderKind.DONOR_REPORT, true);
        settings.setDonorReport(15, LocalTime.of(9, 0));
        recipients.add(new Recipient(UUID.randomUUID(), "Pak Budi", "081234567890", "", true));
        lastActive = LocalDate.of(2026, 11, 15);       // keep the daily ones out of the way
        streak = 0;

        at(14, 12, 0);
        assertTrue(service.tick().isEmpty());
        at(15, 9, 0);
        List<AppNotification> d = service.tick();
        assertEquals(List.of("donor-report"), ids(d));
        assertTrue(d.get(0).getMessage().contains("Pak Budi"));
        at(15, 18, 0);
        assertTrue(ids(service.tick()).stream().noneMatch(i -> i.equals("donor-report")));
        at(16, 12, 0);
        assertTrue(ids(service.tick()).stream().noneMatch(i -> i.equals("donor-report")));
    }

    @Test
    void day31InFebruaryFiresOnTheLastDayThroughTheService() throws Exception {
        settings.setEnabled(ReminderKind.DONOR_REPORT, true);
        settings.setDonorReport(31, LocalTime.of(9, 0));
        recipients.add(new Recipient(UUID.randomUUID(), "A", "081234567890", "", true));
        lastActive = LocalDate.of(2027, 2, 28);

        clock.setLocal(LocalDateTime.of(2027, 2, 27, 10, 0));
        assertTrue(ids(service.tick()).stream().noneMatch(i -> i.equals("donor-report")));
        clock.setLocal(LocalDateTime.of(2027, 2, 28, 10, 0));
        assertEquals(1, ids(service.tick()).stream().filter(i -> i.equals("donor-report")).count());
        clock.setLocal(LocalDateTime.of(2027, 3, 1, 10, 0));
        assertTrue(ids(service.tick()).stream().noneMatch(i -> i.equals("donor-report")));
    }

    // =========================================================================
    // restart: nothing repeats, what was missed today fires, older days do not
    // =========================================================================

    @Test
    void afterARestartNothingThatAlreadyFiredFiresAgain() throws Exception {
        at(21, 20, 0);
        assertEquals(1, service.tick().size());
        log.flush();
        center.flush();

        ReminderLog logAfter = new ReminderLog(new JsonRepository<>(dir.resolve("log.json"), ReminderLogDto.class));
        logAfter.load();
        NotificationCenter centerAfter = new NotificationCenter(new JsonRepository<>(dir.resolve("notifications.json"), NotificationDto.class));
        centerAfter.load();
        ReminderService restarted = newService(settings, logAfter, centerAfter);

        at(21, 20, 30);
        assertTrue(restarted.tick().isEmpty(), "same day, already fired before the restart");
        assertEquals(1, centerAfter.getAll().size(), "and the old notification is still there");
        at(22, 20, 0);
        assertEquals(1, restarted.tick().size(), "a new day fires again");
    }

    @Test
    void startingTheServiceCatchesUpOnWhatWasMissedEarlierToday() {
        at(21, 22, 30);                                  // the app was closed at 20:00 and opened at 22:30
        service.start();                                  // ticks immediately (the UI executor runs it right here)
        try {
            assertEquals(List.of("daily-log"), ids(center.getAll()));
        } finally {
            service.stop();
        }
    }

    @Test
    void aMissedReminderFromAnEarlierDayIsNotReplayed() throws Exception {
        settings.setEnabled(ReminderKind.DONOR_REPORT, true);
        settings.setDonorReport(15, LocalTime.of(9, 0));
        recipients.add(new Recipient(UUID.randomUUID(), "A", "081234567890", "", true));
        lastActive = LocalDate.of(2026, 11, 16);

        at(16, 8, 0);                                    // the app was closed all of the 15th and opened on the 16th at 08:00
        service.start();
        try {
            assertTrue(center.getAll().isEmpty(), "yesterday's daily reminder and the monthly one on the 15th are not replayed");
        } finally {
            service.stop();
        }
    }

    // =========================================================================
    // quiet hours
    // =========================================================================

    @Test
    void nothingFiresDuringQuietHours() throws Exception {
        settings.setQuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0));
        settings.setDailyLogTime(LocalTime.of(8, 0));
        at(21, 23, 0);
        assertTrue(service.tick().isEmpty());
        clock.setLocal(LocalDateTime.of(2026, 11, 22, 6, 59));
        assertTrue(service.tick().isEmpty());
    }

    @Test
    void aReminderThatCameDueInQuietHoursFiresWhenTheyEndIfItsDayIsNotOver() throws Exception {
        settings.setQuietHours(true, LocalTime.of(8, 30), LocalTime.of(9, 30));
        settings.setDailyLogTime(LocalTime.of(9, 0));
        at(21, 9, 10);
        assertTrue(service.tick().isEmpty(), "09:00 is inside quiet hours 08:30-09:30");
        at(21, 9, 30);
        assertEquals(List.of("daily-log"), ids(service.tick()), "quiet hours are over: it fires");
    }

    @Test
    void aReminderThatCameDueInQuietHoursIsDroppedWhenItsDayHasEnded() throws Exception {
        settings.setQuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0));
        settings.setDailyLogTime(LocalTime.of(23, 0));
        at(21, 23, 30);
        assertTrue(service.tick().isEmpty());
        clock.setLocal(LocalDateTime.of(2026, 11, 22, 7, 0));
        assertTrue(service.tick().isEmpty(), "a new day: yesterday's evening reminder is gone, today's is not due until 23:00");
    }

    @Test
    void theTestNotificationIgnoresQuietHoursSettingsAndTheLog() throws Exception {
        settings.setQuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0));
        for (ReminderKind k : ReminderKind.values()) settings.setEnabled(k, false);
        at(21, 23, 30);

        AppNotification n = service.sendTest();
        assertEquals("Notifikasi uji", n.getTitle());
        assertNull(n.getReminderId());
        assertEquals(NotificationAction.NONE, n.getAction());
        assertEquals(List.of(n), center.getAll());
        assertEquals(List.of(n), sunk);
        assertEquals(0, log.size(), "a test is not recorded as fired");
        assertTrue(events.stream().noneMatch(e -> e instanceof GameEvent.ReminderFired), "and does not count for the game");
        service.sendTest();
        assertEquals(2, center.getAll().size(), "it can be sent again and again");
    }

    // =========================================================================
    // sleep/wake and time zones: the clock is asked on every tick
    // =========================================================================

    @Test
    void aLongSleepIsNoticedOnTheNextTick() throws Exception {
        streak = 3;
        lastActive = LocalDate.of(2026, 11, 20);
        at(21, 10, 0);
        assertTrue(service.tick().isEmpty());
        clock.advance(Duration.ofHours(11).plusMinutes(30));          // the laptop slept until 21:30
        assertEquals(List.of("daily-log", "streak-risk"), ids(service.tick()));
    }

    @Test
    void sleepingThroughSeveralDaysFiresOnlyTodaysPeriodNotTheMissedDays() {
        at(21, 10, 0);
        service.tick();
        clock.advance(Duration.ofDays(3).plusHours(11));               // 25 Nov 21:00
        assertEquals(List.of("daily-log"), ids(service.tick()), "once, for today only");
    }

    @Test
    void aChangedTimeZoneIsPickedUpBecauseEveryTickReadsTheClockAgain() {
        clock.setLocal(LocalDateTime.of(2026, 11, 21, 15, 0));          // 15:00 in Jakarta: not due
        assertTrue(service.tick().isEmpty());

        clock.changeZone(ZoneId.of("Asia/Tokyo"));                      // the same moment is now 17:00 in Tokyo: still not due
        assertTrue(service.tick().isEmpty());
        clock.changeZone(ZoneId.of("Europe/Amsterdam"));                // 09:00 there
        assertTrue(service.tick().isEmpty());
        clock.changeZone(ZoneId.of("Pacific/Auckland"));                // 21:00 (+13 in November): due now, in the new zone
        assertEquals(List.of("daily-log"), ids(service.tick()));
    }

    @Test
    void theNewZonesDateDecidesThePeriodToo() {
        clock.setLocal(LocalDateTime.of(2026, 11, 21, 20, 30));
        assertEquals(1, service.tick().size());                         // period 2026-11-21
        clock.changeZone(ZoneId.of("Pacific/Auckland"));                // the same moment: 2026-11-22 02:30 there
        assertTrue(service.tick().isEmpty(), "a new date but not 20:00 yet");
        clock.advance(Duration.ofHours(18));                            // 20:30 on the 22nd in Auckland
        assertEquals(1, service.tick().size(), "the 22nd is a new period");
    }

    // =========================================================================
    // failures are contained
    // =========================================================================

    @Test
    void aFailingSinkDoesNotStopTheOthersOrLoseTheNotification() {
        service.addSink(n -> { throw new IllegalStateException("tray broke"); });
        List<AppNotification> later = new ArrayList<>();
        service.addSink(later::add);
        at(21, 20, 0);
        assertEquals(1, service.tick().size());
        assertEquals(1, later.size());
        assertEquals(1, center.getAll().size());
    }

    @Test
    void aFailingContextDoesNotBreakTheTick() {
        ReminderService broken = new ReminderService(settings, log, center,
                () -> { throw new IllegalStateException("data not ready"); }, bus, clock, Runnable::run);
        at(21, 20, 0);
        assertDoesNotThrow(() -> assertTrue(broken.tick().isEmpty()));
    }

    // =========================================================================
    // the timer thread
    // =========================================================================

    @Test
    void theTimerTicksRepeatedlyOnADaemonThreadAndStopsCleanly() throws Exception {
        CountDownLatch ticks = new CountDownLatch(4);
        AtomicReference<Thread> timerThread = new AtomicReference<>();
        AtomicInteger beforeTicks = new AtomicInteger();
        AtomicReference<String> beforeOn = new AtomicReference<>();
        ReminderService timed = new ReminderService(settings, log, center,
                () -> { ticks.countDown(); return context(); }, bus, clock,
                command -> { timerThread.set(Thread.currentThread()); command.run(); },    // stands in for Platform::runLater
                Duration.ofMillis(20), () -> { beforeTicks.incrementAndGet(); beforeOn.set(Thread.currentThread().getName()); });
        timed.start();
        assertTrue(ticks.await(3, TimeUnit.SECONDS), "an immediate tick plus a tick every interval");
        timed.stop();

        assertTrue(timerThread.get().isDaemon(), "must never keep the app alive");
        assertEquals("reminder-ticker", beforeOn.get(), "the hook runs on the timer thread");
        assertTrue(beforeTicks.get() >= 2);
        int seen = (int) ticks.getCount();
        Thread.sleep(120);
        assertEquals(seen, ticks.getCount(), "no more ticks after stop()");
        timed.stop();                                                    // a second stop is harmless
        assertFalse(timerThread.get().isAlive(), "the thread has ended");
    }
}
