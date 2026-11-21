package com.okto.jalokduitnow.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** "5 menit lalu", "kemarin" ... for the notification list. Pure: both moments are passed in. */
public final class TimeAgo {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Rupiah.ID);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", Rupiah.ID);

    private TimeAgo() {}

    public static String format(Instant then, Instant now, ZoneId zone) {
        Duration age = Duration.between(then, now);
        if (age.isNegative() || age.toMinutes() < 1) return "baru saja";
        if (age.toMinutes() < 60) return age.toMinutes() + " menit lalu";

        LocalDate thenDay = LocalDate.ofInstant(then, zone);
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (thenDay.equals(today)) return age.toHours() + " jam lalu";
        if (thenDay.equals(today.minusDays(1))) return "kemarin, " + then.atZone(zone).format(HOUR);
        return thenDay.format(DAY);
    }
}
