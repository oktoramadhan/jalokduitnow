package com.okto.jalokduitnow.report;

/**
 * What a report may include beyond the summary.
 *
 * @param includeDetails  list the individual transactions (off by default: it shares more)
 * @param choiceColumnId  id of a Choice column to break spending down by (e.g. Need vs Want), or null for none
 */
public record ReportOptions(boolean includeDetails, String choiceColumnId) {
    public static ReportOptions defaults() {
        return new ReportOptions(false, null);
    }
}
