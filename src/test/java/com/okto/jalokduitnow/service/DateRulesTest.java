package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DateRulesTest {
    private final TestClock clock = new TestClock(LocalDate.of(2026, 11, 21));

    @Test
    void anyOrdinaryYearIsAccepted() {
        for (LocalDate d : new LocalDate[]{LocalDate.of(2024, 6, 15), LocalDate.of(2026, 11, 21), LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 12, 31), LocalDate.of(2030, 5, 5), LocalDate.of(2012, 3, 1)}) {
            assertDoesNotThrow(() -> DateRules.check(d, clock), d.toString());
        }
    }

    @Test
    void datesBeforeTheFirstPlausibleYearAreRefusedWithAClearMessage() {
        assertDoesNotThrow(() -> DateRules.check(DateRules.earliest(), clock));                      // the boundary itself is fine
        InvalidTransactionException e = assertThrows(InvalidTransactionException.class,
                () -> DateRules.check(DateRules.earliest().minusDays(1), clock));
        assertTrue(e.getMessage().contains("tidak masuk akal"));
        assertTrue(e.getMessage().contains(String.valueOf(DateRules.EARLIEST_YEAR)), "says what is allowed: " + e.getMessage());
    }

    @Test
    void datesMoreThanFiveYearsAheadAreRefused() {
        LocalDate limit = clock.today().plusYears(5);
        assertDoesNotThrow(() -> DateRules.check(limit, clock));
        InvalidTransactionException e = assertThrows(InvalidTransactionException.class,
                () -> DateRules.check(limit.plusDays(1), clock));
        assertTrue(e.getMessage().contains(String.valueOf(limit.getYear())));
    }

    @Test
    void theUpperLimitMovesWithTheClock() {
        clock.set(LocalDate.of(2030, 1, 1));
        assertEquals(LocalDate.of(2035, 1, 1), DateRules.latest(clock));
        assertDoesNotThrow(() -> DateRules.check(LocalDate.of(2034, 6, 1), clock));
        assertThrows(InvalidTransactionException.class, () -> DateRules.check(LocalDate.of(2036, 1, 1), clock));
    }

    @Test
    void aLeapDayAndANullDate() {
        assertDoesNotThrow(() -> DateRules.check(LocalDate.of(2028, 2, 29), clock));
        assertThrows(InvalidTransactionException.class, () -> DateRules.check(null, clock));
    }
}
