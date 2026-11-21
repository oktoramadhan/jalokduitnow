package com.okto.jalokduitnow.thread;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Runs a task every midnight (by the injected Clock) on a ScheduledExecutorService.
 * The timer thread never touches game data: it hands the task to {@code uiExecutor} (Platform::runLater in
 * the app), so everything the task does happens on the FX thread.
 */
public class DailyScheduler {
    private final Runnable task;
    private final Executor uiExecutor;
    private final Function<Clock, Duration> delayUntilNext;
    private final Clock clock;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "daily-scheduler");
        t.setDaemon(true);
        return t;
    });

    public DailyScheduler(Clock clock, Runnable task, Executor uiExecutor) {
        this(clock, task, uiExecutor, DailyScheduler::untilNextMidnight);
    }

    /** The delay function is injectable so tests do not have to wait for a real midnight. */
    public DailyScheduler(Clock clock, Runnable task, Executor uiExecutor, Function<Clock, Duration> delayUntilNext) {
        this.clock = clock;
        this.task = task;
        this.uiExecutor = uiExecutor;
        this.delayUntilNext = delayUntilNext;
    }

    public void start() {
        scheduleNext();
    }

    public void stop() {
        timer.shutdownNow();
    }

    private void scheduleNext() {
        long millis = Math.max(1, delayUntilNext.apply(clock).toMillis());
        timer.schedule(this::fire, millis, TimeUnit.MILLISECONDS);
    }

    private void fire() {
        uiExecutor.execute(task);
        scheduleNext();
    }

    /** Time from now until the start of the next day (plus one second, so the date has surely changed). */
    public static Duration untilNextMidnight(Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        return Duration.between(now, tomorrow.atStartOfDay(clock.getZone())).plusSeconds(1);
    }
}
