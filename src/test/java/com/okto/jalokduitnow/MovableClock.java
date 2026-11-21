package com.okto.jalokduitnow;

import java.time.*;

/** A clock whose instant AND time zone can both be changed, to test sleep/wake and time zone changes. */
public final class MovableClock extends Clock {
    private Instant instant;
    private ZoneId zone;

    public MovableClock(LocalDateTime local, ZoneId zone) {
        this.zone = zone;
        this.instant = local.atZone(zone).toInstant();
    }

    public void setLocal(LocalDateTime local) { this.instant = local.atZone(zone).toInstant(); }
    public void advance(Duration d) { this.instant = instant.plus(d); }
    /** The same moment, seen in another time zone (the user flew somewhere). */
    public void changeZone(ZoneId newZone) { this.zone = newZone; }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId z) { throw new UnsupportedOperationException(); }
    @Override public Instant instant() { return instant; }
}
