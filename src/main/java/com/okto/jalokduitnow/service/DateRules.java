package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidTransactionException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Sanity check for a transaction date. The app puts no artificial limit on dates (any year works, including years
 * before your first entry and years to come); this only rejects dates that are obviously a typo: before year
 * {@value #EARLIEST_YEAR}, or more than {@value #MAX_YEARS_AHEAD} years after today (by the injected Clock).
 */
public final class DateRules {
    /** Earliest plausible year for a personal finance entry. A plausibility limit, not a range of the app. */
    public static final int EARLIEST_YEAR = 2000;
    public static final int MAX_YEARS_AHEAD = 5;
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("d MMMM yyyy", com.okto.jalokduitnow.util.Rupiah.ID);

    private DateRules() {}

    public static LocalDate earliest() {
        return LocalDate.of(EARLIEST_YEAR, 1, 1);
    }

    public static LocalDate latest(Clock clock) {
        return LocalDate.now(clock).plusYears(MAX_YEARS_AHEAD);
    }

    /** @throws InvalidTransactionException with a message that says what is allowed */
    public static void check(LocalDate date, Clock clock) throws InvalidTransactionException {
        if (date == null) throw new InvalidTransactionException("Tanggal wajib diisi");
        if (date.isBefore(earliest()) || date.isAfter(latest(clock))) {
            throw new InvalidTransactionException("Tanggal " + date.format(SHOWN) + " tidak masuk akal. Gunakan tanggal antara "
                    + earliest().format(SHOWN) + " dan " + latest(clock).format(SHOWN) + ".");
        }
    }
}
