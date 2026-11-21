package com.okto.jalokduitnow.service;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Which months and years the app offers, computed from the data and the injected Clock. No year is ever written
 * into the code: the range moves with the date, so the app keeps working in any future year.
 * Pure functions, so they are tested with fixed clocks and called from the UI.
 */
public final class PeriodRange {
    private PeriodRange() {}

    /** The current month according to the clock. */
    public static YearMonth current(Clock clock) {
        return YearMonth.now(clock);
    }

    /**
     * Years for a year picker, with no gaps: from the earliest year that has data (or the current year if there
     * is none) up to next year. The range is widened to also include the current year, any year that has data and the
     * year that is selected, so nothing the user already has or navigated to ever disappears from the picker.
     */
    public static List<Integer> years(Collection<YearMonth> dataMonths, YearMonth selected, Clock clock) {
        int currentYear = current(clock).getYear();
        int first = currentYear;
        int last = currentYear + 1;
        for (YearMonth m : dataMonths) {
            first = Math.min(first, m.getYear());
            last = Math.max(last, m.getYear());
        }
        if (selected != null) {
            first = Math.min(first, selected.getYear());
            last = Math.max(last, selected.getYear());
        }
        List<Integer> years = new ArrayList<>();
        for (int y = first; y <= last; y++) years.add(y);
        return years;
    }

    /** The month to show first: the current month, or the latest month that has data if the current one is empty. */
    public static YearMonth defaultMonth(Collection<YearMonth> dataMonths, Clock clock) {
        YearMonth now = current(clock);
        if (dataMonths.contains(now) || dataMonths.isEmpty()) return now;
        return Collections.max(dataMonths);
    }

    /** The {@code count} months ending at {@code end} (inclusive), oldest first. Crosses year boundaries naturally. */
    public static List<YearMonth> lastMonths(YearMonth end, int count) {
        List<YearMonth> months = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) months.add(end.minusMonths(i));
        return months;
    }

    /** Moves a month forward or backward; December plus one month is January of the next year. */
    public static YearMonth shift(YearMonth month, int months) {
        return month.plusMonths(months);
    }
}
