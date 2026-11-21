package com.okto.jalokduitnow.thread;

import com.okto.jalokduitnow.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DailySchedulerTest {
    @Test
    void waitsUntilJustAfterTheNextMidnight() {
        TestClock noon = new TestClock(LocalDate.of(2026, 11, 20));                 // 12:00
        assertEquals(Duration.ofHours(12).plusSeconds(1), DailyScheduler.untilNextMidnight(noon));
    }

    @Test
    void justBeforeMidnightTheWaitIsShort() {
        Clock late = Clock.fixed(Instant.parse("2026-11-20T16:59:30Z"), TestClock.ZONE);   // 23:59:30 Jakarta
        assertEquals(Duration.ofSeconds(31), DailyScheduler.untilNextMidnight(late));
    }

    @Test
    void theTaskRunsOnTheUiExecutorNotOnTheTimerThreadAndKeepsRepeating() throws Exception {
        CountDownLatch twice = new CountDownLatch(2);
        AtomicReference<String> executedOn = new AtomicReference<>();
        AtomicReference<String> handedOffBy = new AtomicReference<>();

        DailyScheduler scheduler = new DailyScheduler(Clock.systemDefaultZone(),
                () -> { executedOn.set(Thread.currentThread().getName()); twice.countDown(); },
                command -> { handedOffBy.set(Thread.currentThread().getName()); command.run(); },   // stands in for Platform::runLater
                clock -> Duration.ofMillis(30));
        scheduler.start();
        assertTrue(twice.await(3, TimeUnit.SECONDS), "task should fire repeatedly");
        scheduler.stop();

        assertEquals("daily-scheduler", handedOffBy.get());      // the timer thread only hands the task over
        assertNotNull(executedOn.get());
    }
}
