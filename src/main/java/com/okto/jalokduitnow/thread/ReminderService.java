package com.okto.jalokduitnow.thread;

import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.reminder.*;
import com.okto.jalokduitnow.service.NotificationCenter;
import com.okto.jalokduitnow.service.ReminderLog;
import com.okto.jalokduitnow.service.ReminderSettingsService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Checks the reminders every minute and delivers the ones that are due.
 *
 * <ul>
 *   <li><b>Threading:</b> a daemon ScheduledExecutorService only keeps time. Each tick is handed to the UI executor
 *       (Platform::runLater in the app), so the data is read and the UI is updated on the FX thread only.</li>
 *   <li><b>Time:</b> nothing is scheduled for a specific moment. Every tick asks the Clock what time it is now and
 *       whether each reminder is due, so a laptop that slept, a clock that jumped or a changed time zone is simply
 *       noticed on the next tick (there is no delay that could drift or be missed).</li>
 *   <li><b>Once only:</b> a reminder fires at most once per (id, period); the ReminderLog is saved, so a restart does not
 *       repeat it. Starting the service ticks immediately, which fires what was missed earlier today (a reminder from
 *       an earlier day or month is not replayed, its period key no longer matches).</li>
 *   <li><b>Quiet hours:</b> nothing fires during them. A reminder that came due in that time fires on the first tick
 *       after they end, if its day or month is still the same.</li>
 * </ul>
 */
public class ReminderService {
    public static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(1);

    private final ReminderSettingsService settings;
    private final ReminderLog log;
    private final NotificationCenter center;
    private final Supplier<ReminderContext> contextSupplier;
    private final GameEventBus bus;
    private final Clock clock;
    private final Executor uiExecutor;
    private final Duration interval;
    private final Runnable beforeTick;
    private final List<NotificationSink> sinks = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reminder-ticker");
        t.setDaemon(true);   // never keeps the app alive
        return t;
    });

    public ReminderService(ReminderSettingsService settings, ReminderLog log, NotificationCenter center,
                           Supplier<ReminderContext> contextSupplier, GameEventBus bus, Clock clock, Executor uiExecutor) {
        this(settings, log, center, contextSupplier, bus, clock, uiExecutor, DEFAULT_INTERVAL, () -> { });
    }

    /**
     * @param interval   time between ticks (tests use milliseconds)
     * @param beforeTick runs on the timer thread before each tick (the app re-reads the system time zone here)
     */
    public ReminderService(ReminderSettingsService settings, ReminderLog log, NotificationCenter center,
                           Supplier<ReminderContext> contextSupplier, GameEventBus bus, Clock clock, Executor uiExecutor,
                           Duration interval, Runnable beforeTick) {
        this.settings = settings;
        this.log = log;
        this.center = center;
        this.contextSupplier = contextSupplier;
        this.bus = bus;
        this.clock = clock;
        this.uiExecutor = uiExecutor;
        this.interval = interval;
        this.beforeTick = beforeTick;
    }

    public void addSink(NotificationSink sink) {
        sinks.add(sink);
    }

    /** Checks right away (this catches up on what was missed today) and then once per interval. */
    public void start() {
        uiExecutor.execute(this::tick);
        long millis = Math.max(1, interval.toMillis());
        timer.scheduleWithFixedDelay(() -> {
            try {
                beforeTick.run();
                uiExecutor.execute(this::tick);
            } catch (RuntimeException e) {
                System.err.println("Pengecekan pengingat gagal: " + e);   // a bad tick must not stop the timer
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** Stops the timer thread and waits briefly for it. Safe to call more than once. */
    public void stop() {
        timer.shutdownNow();
        try {
            timer.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * One check. Runs on the thread that calls it (the UI executor in the app, the test thread in tests).
     * @return the notifications that were delivered by this check
     */
    public List<AppNotification> tick() {
        List<AppNotification> delivered = new ArrayList<>();
        ReminderSettings current = settings.current();
        LocalDateTime now = LocalDateTime.now(clock);          // the clock is asked again on every tick
        if (current.isQuiet(now.toLocalTime())) return delivered;

        ReminderContext context;
        try {
            context = contextSupplier.get();
        } catch (RuntimeException e) {
            System.err.println("Data pengingat belum siap: " + e);     // skip this tick; the next one tries again
            return delivered;
        }
        for (Reminder reminder : ReminderFactory.build(current, context)) {
            try {
                if (!reminder.isEnabled()) continue;
                String key = reminder.getId() + "@" + reminder.periodKey(clock);
                if (log.hasFired(key) || !reminder.shouldFire(context, clock)) continue;
                delivered.add(fire(reminder, context, key));
            } catch (RuntimeException e) {
                System.err.println("Pengingat " + reminder.getId() + " gagal: " + e);   // one bad reminder must not block the rest
            }
        }
        return delivered;
    }

    /** A test notification: always delivered (ignores quiet hours and the once-only record) and never counts as a reminder. */
    public AppNotification sendTest() {
        AppNotification n = AppNotification.create("Notifikasi uji",
                "Pengingat berfungsi. Notifikasi seperti ini akan muncul saat ada yang perlu kamu ketahui.",
                Instant.now(clock), NotificationAction.NONE, null);
        deliver(n);
        return n;
    }

    private AppNotification fire(Reminder reminder, ReminderContext context, String key) {
        log.record(key, Instant.now(clock));                    // remember first: even if delivery fails it must not repeat
        AppNotification n = AppNotification.create(reminder.getTitle(), reminder.buildMessage(context),
                Instant.now(clock), reminder.action(), reminder.getId());
        deliver(n);
        bus.publish(new GameEvent.ReminderFired(reminder.getId()));
        return n;
    }

    private void deliver(AppNotification n) {
        center.add(n);
        for (NotificationSink sink : sinks) {
            try {
                sink.deliver(n);
            } catch (RuntimeException e) {
                System.err.println("Notifikasi gagal ditampilkan: " + e);   // one failing sink never blocks the others
            }
        }
    }
}
