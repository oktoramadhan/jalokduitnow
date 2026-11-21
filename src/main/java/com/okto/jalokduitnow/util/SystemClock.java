package com.okto.jalokduitnow.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * The real clock, but it asks for the time zone every time instead of remembering the one from startup
 * ({@code Clock.systemDefaultZone()} freezes it). So a laptop that travels, or a changed system time zone,
 * is picked up by anything that reads the clock again, such as the reminder ticks.
 */
public final class SystemClock extends Clock {
    @Override public ZoneId getZone() { return ZoneId.systemDefault(); }
    @Override public Clock withZone(ZoneId zone) { return Clock.system(zone); }
    @Override public Instant instant() { return Instant.now(); }
}
