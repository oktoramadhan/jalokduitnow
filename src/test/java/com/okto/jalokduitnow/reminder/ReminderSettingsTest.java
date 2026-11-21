package com.okto.jalokduitnow.reminder;

import com.okto.jalokduitnow.exception.InvalidSettingException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ReminderSettingsTest {
    @Test
    void defaultsAreSensible() {
        ReminderSettings s = new ReminderSettings();
        assertTrue(s.isEnabled(ReminderKind.DAILY_LOG));
        assertTrue(s.isEnabled(ReminderKind.STREAK_RISK));
        assertTrue(s.isEnabled(ReminderKind.BUDGET));
        assertFalse(s.isEnabled(ReminderKind.DONOR_REPORT), "needs a donor first, so it starts off");
        assertEquals(LocalTime.of(20, 0), s.dailyLogTime());
        assertEquals(LocalTime.of(21, 0), s.streakRiskTime());
        assertEquals(1, s.donorReportDay());
        assertEquals(80, s.budgetWarnPercent());
        assertTrue(s.isQuietEnabled());
        assertEquals(LocalTime.of(22, 0), s.quietStart());
        assertEquals(LocalTime.of(7, 0), s.quietEnd());
        assertTrue(s.isTrayEnabled());
        for (ReminderKind k : ReminderKind.values()) {
            assertFalse(k.title().isBlank());
            assertFalse(k.description().isBlank());
        }
    }

    @Test
    void validValuesAreAcceptedAndTimesAreWholeMinutes() throws Exception {
        ReminderSettings s = new ReminderSettings();
        s.setDailyLogTime(LocalTime.of(18, 45, 30, 123));
        assertEquals(LocalTime.of(18, 45), s.dailyLogTime());
        s.setStreakRiskTime(LocalTime.MIDNIGHT);
        s.setDonorReport(31, LocalTime.of(8, 15));
        s.setBudgetWarnPercent(50);
        s.setBudgetWarnPercent(99);
        s.setEnabled(ReminderKind.DONOR_REPORT, true);
        s.setTrayEnabled(false);
        assertEquals(31, s.donorReportDay());
        assertEquals(99, s.budgetWarnPercent());
        assertTrue(s.isEnabled(ReminderKind.DONOR_REPORT));
        assertFalse(s.isTrayEnabled());
    }

    @Test
    void theDayOfMonthMustBeBetween1And31() throws Exception {
        ReminderSettings s = new ReminderSettings();
        for (int bad : new int[]{0, -1, 32, 100}) {
            assertThrows(InvalidSettingException.class, () -> s.setDonorReport(bad, LocalTime.NOON), "day " + bad);
        }
        assertDoesNotThrow(() -> s.setDonorReport(1, LocalTime.NOON));
        assertDoesNotThrow(() -> s.setDonorReport(31, LocalTime.NOON));
        assertEquals(31, s.donorReportDay());
    }

    @Test
    void theWarningPercentMustBeBetween50And99() {
        ReminderSettings s = new ReminderSettings();
        for (int bad : new int[]{0, 49, 100, 150, -5}) {
            assertThrows(InvalidSettingException.class, () -> s.setBudgetWarnPercent(bad), "percent " + bad);
        }
        assertEquals(80, s.budgetWarnPercent(), "a refused change leaves the old value");
    }

    @Test
    void missingTimesAreRefused() {
        ReminderSettings s = new ReminderSettings();
        assertThrows(InvalidSettingException.class, () -> s.setDailyLogTime(null));
        assertThrows(InvalidSettingException.class, () -> s.setStreakRiskTime(null));
        assertThrows(InvalidSettingException.class, () -> s.setDonorReport(5, null));
        assertThrows(InvalidSettingException.class, () -> s.setQuietHours(true, null, LocalTime.NOON));
        assertEquals(1, s.donorReportDay(), "a refused change applies nothing");
    }

    @Test
    void quietHoursCannotStartAndEndAtTheSameTimeWhileOn() throws Exception {
        ReminderSettings s = new ReminderSettings();
        InvalidSettingException e = assertThrows(InvalidSettingException.class,
                () -> s.setQuietHours(true, LocalTime.of(22, 0), LocalTime.of(22, 0)));
        assertTrue(e.getMessage().contains("tidak boleh sama"));
        assertDoesNotThrow(() -> s.setQuietHours(false, LocalTime.of(22, 0), LocalTime.of(22, 0)));   // off: nothing to compare
        assertFalse(s.isQuietEnabled());
    }

    @Test
    void quietHoursThatStayInsideADay() throws Exception {
        ReminderSettings s = new ReminderSettings();
        s.setQuietHours(true, LocalTime.of(13, 0), LocalTime.of(15, 0));
        assertFalse(s.isQuiet(LocalTime.of(12, 59)));
        assertTrue(s.isQuiet(LocalTime.of(13, 0)), "the start is quiet");
        assertTrue(s.isQuiet(LocalTime.of(14, 59)));
        assertFalse(s.isQuiet(LocalTime.of(15, 0)), "the end is not quiet");
        assertFalse(s.isQuiet(LocalTime.of(0, 0)));
    }

    @Test
    void quietHoursThatCrossMidnight() {
        ReminderSettings s = new ReminderSettings();   // default 22:00 - 07:00
        assertFalse(s.isQuiet(LocalTime.of(21, 59)));
        assertTrue(s.isQuiet(LocalTime.of(22, 0)));
        assertTrue(s.isQuiet(LocalTime.of(23, 59)));
        assertTrue(s.isQuiet(LocalTime.MIDNIGHT));
        assertTrue(s.isQuiet(LocalTime.of(6, 59)));
        assertFalse(s.isQuiet(LocalTime.of(7, 0)));
        assertFalse(s.isQuiet(LocalTime.NOON));
    }

    @Test
    void quietHoursOffMeansNeverQuiet() throws Exception {
        ReminderSettings s = new ReminderSettings();
        s.setQuietHours(false, LocalTime.of(22, 0), LocalTime.of(7, 0));
        assertFalse(s.isQuiet(LocalTime.of(23, 0)));
    }

    @Test
    void aCopyIsIndependent() throws Exception {
        ReminderSettings original = new ReminderSettings();
        original.setBudgetWarnPercent(60);
        ReminderSettings copy = original.copy();
        assertEquals(60, copy.budgetWarnPercent());
        copy.setBudgetWarnPercent(90);
        copy.setEnabled(ReminderKind.DAILY_LOG, false);
        assertEquals(60, original.budgetWarnPercent());
        assertTrue(original.isEnabled(ReminderKind.DAILY_LOG));
    }
}
