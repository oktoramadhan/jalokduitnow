package com.okto.jalokduitnow.quest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** An inclusive date range; quests last one week (Monday to Sunday). */
public record QuestPeriod(LocalDate start, LocalDate end) {
    public QuestPeriod {
        if (end.isBefore(start)) throw new IllegalArgumentException("end before start");
    }

    /** The Monday-Sunday week that contains {@code day}. */
    public static QuestPeriod weekOf(LocalDate day) {
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new QuestPeriod(monday, monday.plusDays(6));
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(start) && !day.isAfter(end);
    }

    /** True once the last day of the period is in the past. */
    public boolean isOver(LocalDate today) {
        return today.isAfter(end);
    }

    public int days() {
        return (int) (end.toEpochDay() - start.toEpochDay()) + 1;
    }
}
