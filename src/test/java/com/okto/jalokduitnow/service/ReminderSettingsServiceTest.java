package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidSettingException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.reminder.ReminderKind;
import com.okto.jalokduitnow.reminder.ReminderSettings;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.ReminderLogDto;
import com.okto.jalokduitnow.storage.ReminderSettingsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ReminderSettingsServiceTest {
    @TempDir Path dir;
    ReminderSettingsService service;

    @BeforeEach
    void setUp() throws Exception {
        service = newService();
        service.load();
    }

    private ReminderSettingsService newService() {
        return new ReminderSettingsService(new JsonRepository<>(dir.resolve("reminders.json"), ReminderSettingsDto.class));
    }

    @Test
    void aFirstRunHasTheDefaults() {
        ReminderSettings s = service.current();
        assertEquals(LocalTime.of(20, 0), s.dailyLogTime());
        assertEquals(80, s.budgetWarnPercent());
        assertFalse(s.isEnabled(ReminderKind.DONOR_REPORT));
    }

    @Test
    void everySettingSurvivesARestart() throws Exception {
        service.setEnabled(ReminderKind.DONOR_REPORT, true);
        service.setEnabled(ReminderKind.STREAK_RISK, false);
        service.setDailyLogTime(LocalTime.of(18, 45));
        service.setStreakRiskTime(LocalTime.of(20, 15));
        service.setDonorReport(31, LocalTime.of(8, 30));
        service.setBudgetWarnPercent(65);
        service.setQuietHours(true, LocalTime.of(23, 0), LocalTime.of(6, 30));
        service.setTrayEnabled(false);
        service.flush();

        ReminderSettingsService again = newService();
        again.load();
        ReminderSettings s = again.current();
        assertTrue(s.isEnabled(ReminderKind.DONOR_REPORT));
        assertFalse(s.isEnabled(ReminderKind.STREAK_RISK));
        assertTrue(s.isEnabled(ReminderKind.DAILY_LOG));
        assertEquals(LocalTime.of(18, 45), s.dailyLogTime());
        assertEquals(LocalTime.of(20, 15), s.streakRiskTime());
        assertEquals(31, s.donorReportDay());
        assertEquals(LocalTime.of(8, 30), s.donorReportTime());
        assertEquals(65, s.budgetWarnPercent());
        assertTrue(s.isQuietEnabled());
        assertEquals(LocalTime.of(23, 0), s.quietStart());
        assertEquals(LocalTime.of(6, 30), s.quietEnd());
        assertFalse(s.isTrayEnabled());
    }

    @Test
    void theFileIsReadableJson() throws Exception {
        service.setDailyLogTime(LocalTime.of(18, 45));
        service.flush();
        String json = Files.readString(dir.resolve("reminders.json"));
        assertTrue(json.contains("dailyLogTime"));
        assertTrue(json.contains("18:45"));
    }

    @Test
    void aRefusedChangeChangesAndSavesNothingAndNotifiesNobody() throws Exception {
        int[] calls = {0};
        service.addListener(() -> calls[0]++);
        assertThrows(InvalidSettingException.class, () -> service.setDonorReport(40, LocalTime.NOON));
        assertThrows(InvalidSettingException.class, () -> service.setBudgetWarnPercent(10));
        assertThrows(InvalidSettingException.class, () -> service.setQuietHours(true, LocalTime.of(5, 0), LocalTime.of(5, 0)));
        assertEquals(0, calls[0]);
        assertEquals(1, service.current().donorReportDay());
        assertEquals(80, service.current().budgetWarnPercent());
        service.flush();
        assertFalse(Files.exists(dir.resolve("reminders.json")), "nothing was saved");
    }

    @Test
    void aPartiallyValidMultiFieldChangeIsAllOrNothing() throws Exception {
        service.setDonorReport(10, LocalTime.of(9, 0));
        assertThrows(InvalidSettingException.class, () -> service.setDonorReport(15, null));    // valid day, missing time
        assertEquals(10, service.current().donorReportDay(), "the valid day was not applied on its own");
    }

    @Test
    void currentReturnsACopyThatCannotChangeTheLiveSettings() throws Exception {
        ReminderSettings copy = service.current();
        copy.setBudgetWarnPercent(55);
        copy.setEnabled(ReminderKind.DAILY_LOG, false);
        assertEquals(80, service.current().budgetWarnPercent());
        assertTrue(service.current().isEnabled(ReminderKind.DAILY_LOG));
    }

    @Test
    void listenersHearEachValidChange() throws Exception {
        int[] calls = {0};
        service.addListener(() -> calls[0]++);
        service.setTrayEnabled(false);
        service.setDailyLogTime(LocalTime.of(19, 0));
        assertEquals(2, calls[0]);
    }

    @Test
    void anOlderFileWithMissingFieldsGetsDefaultsForThem() throws Exception {
        Files.writeString(dir.resolve("reminders.json"), "[{\"budgetWarnPercent\": 70}]");
        ReminderSettingsService again = newService();
        again.load();
        assertEquals(70, again.current().budgetWarnPercent());
        assertEquals(LocalTime.of(20, 0), again.current().dailyLogTime());
        assertTrue(again.current().isEnabled(ReminderKind.DAILY_LOG));
    }

    @Test
    void aHandEditedFileWithAnImpossibleValueIsRefusedNotTrusted() throws Exception {
        Files.writeString(dir.resolve("reminders.json"), "[{\"donorReportDay\": 40}]");
        StorageException e = assertThrows(StorageException.class, newService()::load);
        assertTrue(e.getMessage().contains("rusak"));
    }

    @Test
    void aCorruptFileIsAStorageErrorAndKeepsACopy() throws Exception {
        Files.writeString(dir.resolve("reminders.json"), "{ nope");
        assertThrows(StorageException.class, newService()::load);
        try (var files = Files.list(dir)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    // ---- the fired log ----

    @Test
    void theFiredLogRemembersKeysAcrossARestart() throws Exception {
        ReminderLog log = new ReminderLog(new JsonRepository<>(dir.resolve("log.json"), ReminderLogDto.class));
        log.load();
        assertFalse(log.hasFired("daily-log@2026-11-21"));
        log.record("daily-log@2026-11-21", Instant.parse("2026-11-21T13:00:00Z"));
        assertTrue(log.hasFired("daily-log@2026-11-21"));
        assertFalse(log.hasFired("daily-log@2026-11-22"));
        log.flush();

        ReminderLog again = new ReminderLog(new JsonRepository<>(dir.resolve("log.json"), ReminderLogDto.class));
        again.load();
        assertTrue(again.hasFired("daily-log@2026-11-21"));
        assertEquals(1, again.size());
    }

    @Test
    void oldRecordsAreForgottenAfterTwoMonths() throws Exception {
        ReminderLog log = new ReminderLog(new JsonRepository<>(dir.resolve("log.json"), ReminderLogDto.class));
        log.load();
        log.record("daily-log@2026-09-01", Instant.parse("2026-09-01T13:00:00Z"));
        log.record("daily-log@2026-10-15", Instant.parse("2026-10-15T13:00:00Z"));
        log.record("daily-log@2026-11-21", Instant.parse("2026-11-21T13:00:00Z"));
        assertFalse(log.hasFired("daily-log@2026-09-01"), "more than 62 days old");
        assertTrue(log.hasFired("daily-log@2026-10-15"));
        assertTrue(log.hasFired("daily-log@2026-11-21"));
    }
}
