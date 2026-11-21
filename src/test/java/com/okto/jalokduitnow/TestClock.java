package com.okto.jalokduitnow;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** A clock the test can move. Always at noon of the chosen day in Asia/Jakarta, so "today" is unambiguous. */
public final class TestClock extends Clock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");
    private Instant now;

    public TestClock(LocalDate day) {
        set(day);
    }

    public void set(LocalDate day) {
        now = day.atTime(12, 0).atZone(ZONE).toInstant();
    }

    public LocalDate today() {
        return LocalDate.now(this);
    }

    @Override public ZoneId getZone() { return ZONE; }
    @Override public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
    @Override public Instant instant() { return now; }
}
