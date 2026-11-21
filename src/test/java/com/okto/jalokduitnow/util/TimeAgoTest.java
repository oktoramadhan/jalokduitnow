package com.okto.jalokduitnow.util;

import com.okto.jalokduitnow.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeAgoTest {
    private static final ZoneId Z = TestClock.ZONE;
    private final Instant now = LocalDateTime.of(2026, 11, 21, 15, 0).atZone(Z).toInstant();

    @Test
    void recentTimesAreInMinutesAndHours() {
        assertEquals("baru saja", TimeAgo.format(now.minusSeconds(20), now, Z));
        assertEquals("baru saja", TimeAgo.format(now.plusSeconds(30), now, Z), "a moment in the future is not negative text");
        assertEquals("1 menit lalu", TimeAgo.format(now.minus(Duration.ofMinutes(1)), now, Z));
        assertEquals("59 menit lalu", TimeAgo.format(now.minus(Duration.ofMinutes(59)), now, Z));
        assertEquals("1 jam lalu", TimeAgo.format(now.minus(Duration.ofMinutes(60)), now, Z));
        assertEquals("5 jam lalu", TimeAgo.format(now.minus(Duration.ofHours(5)), now, Z));
    }

    @Test
    void yesterdayAndOlderUseTheCalendarDay() {
        Instant lateYesterday = LocalDateTime.of(2026, 11, 20, 23, 30).atZone(Z).toInstant();
        assertEquals("kemarin, 23:30", TimeAgo.format(lateYesterday, now, Z));
        Instant older = LocalDateTime.of(2026, 11, 5, 9, 0).atZone(Z).toInstant();
        assertEquals("5 Nov 2026", TimeAgo.format(older, now, Z));
    }

    @Test
    void justAfterMidnightIsYesterdayEvenIfFewHoursAgo() {
        Instant justAfterMidnight = LocalDateTime.of(2026, 11, 22, 0, 10).atZone(Z).toInstant();
        Instant morning = LocalDateTime.of(2026, 11, 22, 8, 0).atZone(Z).toInstant();
        assertEquals("7 jam lalu", TimeAgo.format(justAfterMidnight, morning, Z));
        Instant tonight = LocalDateTime.of(2026, 11, 21, 23, 50).atZone(Z).toInstant();
        assertEquals("kemarin, 23:50", TimeAgo.format(tonight, morning, Z));
    }
}
