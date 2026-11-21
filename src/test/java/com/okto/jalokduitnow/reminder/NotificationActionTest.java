package com.okto.jalokduitnow.reminder;

import com.okto.jalokduitnow.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class NotificationActionTest {
    TestClock clock = new TestClock(LocalDate.of(2026, 11, 21));

    @Test
    void theRestoreButtonIsVisibleForExactlyFifteenMinutes() {
        clock.set(LocalDate.of(2026, 11, 21), LocalTime.of(10, 0));
        Instant created = clock.instant();
        assertTrue(NotificationAction.OPEN_RESTORE.isAvailable(created, clock.instant()), "right away");

        clock.set(LocalDate.of(2026, 11, 21), LocalTime.of(10, 14, 59));
        assertTrue(NotificationAction.OPEN_RESTORE.isAvailable(created, clock.instant()), "one second before the end");

        clock.set(LocalDate.of(2026, 11, 21), LocalTime.of(10, 15, 0));
        assertFalse(NotificationAction.OPEN_RESTORE.isAvailable(created, clock.instant()), "at 15 minutes the window is closed");

        clock.set(LocalDate.of(2026, 11, 22), LocalTime.of(10, 0));
        assertFalse(NotificationAction.OPEN_RESTORE.isAvailable(created, clock.instant()), "a day later");
    }

    @Test
    void theWindowIsFifteenMinutesAndOtherActionsNeverExpire() {
        Instant created = clock.instant();
        assertTrue(NotificationAction.OPEN_RESTORE.isAvailable(created, created.plus(Duration.ofMinutes(15)).minusMillis(1)));
        assertFalse(NotificationAction.OPEN_RESTORE.isAvailable(created, created.plus(Duration.ofMinutes(15))));
        for (NotificationAction a : NotificationAction.values()) {
            if (a == NotificationAction.NONE || a == NotificationAction.OPEN_RESTORE) continue;
            assertTrue(a.isAvailable(created, created.plus(Duration.ofDays(400))), a + " has no expiry");
        }
        assertFalse(NotificationAction.NONE.isAvailable(created, created), "no button at all");
    }
}
