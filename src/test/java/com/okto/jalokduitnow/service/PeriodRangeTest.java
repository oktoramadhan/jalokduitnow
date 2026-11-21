package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.TestClock;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** All with fixed clocks. Years appear only as test inputs: the code under test has none. */
class PeriodRangeTest {
    private static TestClock clockAt(int y, int m, int d) {
        return new TestClock(LocalDate.of(y, m, d));
    }

    // ---- year range ----

    @Test
    void withNoDataTheRangeIsThisYearAndNextYear() {
        assertEquals(List.of(2026, 2027), PeriodRange.years(Set.of(), null, clockAt(2026, 11, 21)));
    }

    @Test
    void theRangeMovesWithTheClockNotWithTheCode() {
        assertEquals(List.of(2030, 2031), PeriodRange.years(Set.of(), null, clockAt(2030, 3, 5)));
        assertEquals(List.of(2041, 2042), PeriodRange.years(Set.of(), null, clockAt(2041, 12, 31)));
    }

    @Test
    void dataInTheEarlierPastExtendsTheRangeBackWithoutGaps() {
        Set<YearMonth> data = Set.of(YearMonth.of(2024, 6));
        assertEquals(List.of(2024, 2025, 2026, 2027), PeriodRange.years(data, null, clockAt(2026, 11, 21)));
    }

    @Test
    void dataIn2024And2027GivesEveryYearBetweenThem() {
        Set<YearMonth> data = Set.of(YearMonth.of(2024, 6), YearMonth.of(2027, 3));
        List<Integer> years = PeriodRange.years(data, null, clockAt(2026, 11, 21));
        assertEquals(List.of(2024, 2025, 2026, 2027), years);       // 2025 and 2026 have no data but are still offered
    }

    @Test
    void dataFurtherInTheFutureStillShowsUp() {
        Set<YearMonth> data = Set.of(YearMonth.of(2030, 1));
        assertEquals(List.of(2026, 2027, 2028, 2029, 2030), PeriodRange.years(data, null, clockAt(2026, 11, 21)));
    }

    @Test
    void theSelectedYearIsAlwaysIncluded() {
        assertEquals(List.of(2020, 2021, 2022, 2023, 2024, 2025, 2026, 2027),
                PeriodRange.years(Set.of(), YearMonth.of(2020, 5), clockAt(2026, 11, 21)));
        assertEquals(List.of(2026, 2027, 2028, 2029, 2030, 2031, 2032, 2033, 2034, 2035),
                PeriodRange.years(Set.of(), YearMonth.of(2035, 5), clockAt(2026, 11, 21)));
    }

    @Test
    void onNewYearsDayTheRangeAdvances() {
        assertEquals(List.of(2026, 2027), PeriodRange.years(Set.of(), null, clockAt(2026, 12, 31)));
        assertEquals(List.of(2027, 2028), PeriodRange.years(Set.of(), null, clockAt(2027, 1, 1)));
    }

    @Test
    void dataOlderThanThisYearKeepsTheCurrentYearSelectable() {
        // all data is in the past: the range still reaches the current year and next year
        List<Integer> years = PeriodRange.years(Set.of(YearMonth.of(2020, 1)), null, clockAt(2026, 11, 21));
        assertEquals(2020, years.get(0));
        assertEquals(2027, years.get(years.size() - 1));
    }

    // ---- default month ----

    @Test
    void theDefaultMonthIsTheCurrentMonthWhenItHasData() {
        Set<YearMonth> data = Set.of(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2027, 3));
        assertEquals(YearMonth.of(2026, 11), PeriodRange.defaultMonth(data, clockAt(2026, 11, 21)));
    }

    @Test
    void whenTheCurrentMonthIsEmptyTheLatestMonthWithDataIsUsed() {
        Set<YearMonth> data = Set.of(YearMonth.of(2024, 6), YearMonth.of(2026, 9));
        assertEquals(YearMonth.of(2026, 9), PeriodRange.defaultMonth(data, clockAt(2026, 11, 21)));
    }

    @Test
    void withNoDataTheDefaultMonthIsTheCurrentMonth() {
        assertEquals(YearMonth.of(2027, 2), PeriodRange.defaultMonth(Set.of(), clockAt(2027, 2, 14)));
        assertEquals(YearMonth.of(2027, 1), PeriodRange.current(clockAt(2027, 1, 1)));
    }

    // ---- navigation and charts across New Year ----

    @Test
    void movingAMonthCrossesTheYearBoundaryBothWays() {
        assertEquals(YearMonth.of(2027, 1), PeriodRange.shift(YearMonth.of(2026, 12), 1));
        assertEquals(YearMonth.of(2026, 12), PeriodRange.shift(YearMonth.of(2027, 1), -1));
        assertEquals(YearMonth.of(2025, 12), PeriodRange.shift(YearMonth.of(2026, 1), -1));
        assertEquals(YearMonth.of(2028, 2), PeriodRange.shift(YearMonth.of(2026, 12), 14));
        assertEquals(YearMonth.of(2026, 5), PeriodRange.shift(YearMonth.of(2026, 5), 0));
    }

    @Test
    void theLastSixMonthsRunAcrossNewYearInOrder() {
        assertEquals(List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11),
                        YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2)),
                PeriodRange.lastMonths(YearMonth.of(2027, 2), 6));
        assertEquals(YearMonth.of(2026, 8), PeriodRange.lastMonths(YearMonth.of(2027, 1), 6).get(0));
    }

    @Test
    void theLastSixMonthsWhenTheEndIsDecemberAndJanuary() {
        List<YearMonth> dec = PeriodRange.lastMonths(YearMonth.of(2026, 12), 6);
        assertEquals(YearMonth.of(2026, 7), dec.get(0));
        assertEquals(YearMonth.of(2026, 12), dec.get(5));
        assertEquals(6, dec.stream().distinct().count());
        assertEquals(List.of(YearMonth.of(2027, 1)), PeriodRange.lastMonths(YearMonth.of(2027, 1), 1));
        assertEquals(List.of(), PeriodRange.lastMonths(YearMonth.of(2027, 1), 0));
    }
}
