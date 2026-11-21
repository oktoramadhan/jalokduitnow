package com.okto.jalokduitnow.report;

import java.time.YearMonth;

/**
 * Something that turns one month of data into a plain-text report. The personal report and the donor report are
 * different implementations of this one interface, so the UI can preview and share whichever the user picked
 * without knowing which it is (polymorphism). Reports are plain text only: no emoji, no markup.
 */
public interface ReportGenerator {
    /** Name of this kind of report, for the UI (e.g. "Laporan Pribadi"). */
    String title();

    /** The report text for the month. */
    String generate(YearMonth month);
}
