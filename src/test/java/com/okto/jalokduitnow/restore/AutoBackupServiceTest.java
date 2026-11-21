package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.service.AutoBackupService;
import com.okto.jalokduitnow.service.AutoBackupService.BackupFile;
import com.okto.jalokduitnow.service.AutoBackupService.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AutoBackupServiceTest {
    @TempDir Path dir;
    TestClock clock = new TestClock(LocalDate.of(2026, 11, 21));
    AutoBackupService service;

    @BeforeEach
    void setUp() {
        service = new AutoBackupService(dir.resolve("backups"), clock);
    }

    @Test
    void aDailyBackupIsWrittenOncePerDay() throws Exception {
        Optional<Path> first = service.writeDailyIfMissing("{\"v\":1}");
        assertTrue(first.isPresent());
        assertEquals("otomatis-2026-11-21.json", first.get().getFileName().toString());
        assertEquals("{\"v\":1}", Files.readString(first.get()));

        assertTrue(service.writeDailyIfMissing("{\"v\":2}").isEmpty(), "the same day: no second file");
        assertEquals("{\"v\":1}", Files.readString(first.get()), "and the first one is not overwritten");

        clock.set(LocalDate.of(2026, 11, 22));
        assertTrue(service.writeDailyIfMissing("{\"v\":3}").isPresent(), "a new day gets its own");
        assertEquals(2, service.list().size());
    }

    @Test
    void theDailyDateFollowsTheClockAcrossTheYearEnd() throws Exception {
        clock.set(LocalDate.of(2026, 12, 31));
        assertEquals("otomatis-2026-12-31.json", service.writeDailyIfMissing("x").orElseThrow().getFileName().toString());
        clock.set(LocalDate.of(2027, 1, 1));
        assertEquals("otomatis-2027-01-01.json", service.writeDailyIfMissing("x").orElseThrow().getFileName().toString());
    }

    @Test
    void safetyBackupsGetATimestampedNameAndNeverCollide() throws Exception {
        clock.set(LocalDate.of(2026, 11, 21), LocalTime.of(16, 5, 9));
        Path a = service.writeSafety("one");
        Path b = service.writeSafety("two");                                 // the same second
        assertEquals("sebelum-pemulihan-20261121-160509.json", a.getFileName().toString());
        assertEquals("sebelum-pemulihan-20261121-160509-2.json", b.getFileName().toString());
        assertEquals("one", Files.readString(a));
        assertEquals("two", Files.readString(b));
    }

    @Test
    void theListShowsKindsNewestFirst() throws Exception {
        clock.set(LocalDate.of(2026, 11, 20), LocalTime.of(9, 0));
        service.writeDailyIfMissing("old");
        clock.set(LocalDate.of(2026, 11, 21), LocalTime.of(9, 0));
        service.writeDailyIfMissing("newer");
        clock.set(LocalDate.of(2026, 11, 21), LocalTime.of(16, 0));
        service.writeSafety("newest");

        List<BackupFile> list = service.list();
        assertEquals(List.of(Kind.SAFETY, Kind.AUTO, Kind.AUTO), list.stream().map(BackupFile::kind).toList());
        assertTrue(list.get(0).fileName().startsWith("sebelum-pemulihan-"));
        assertEquals("otomatis-2026-11-21.json", list.get(1).fileName());
        assertTrue(list.get(0).size() > 0);
    }

    @Test
    void onlyTheLastFiveDailyBackupsAreKept() throws Exception {
        assertEquals(5, AutoBackupService.KEEP_AUTO);
        LocalDate day = LocalDate.of(2026, 10, 1);
        for (int i = 0; i < AutoBackupService.KEEP_AUTO + 6; i++) {
            clock.set(day.plusDays(i));
            service.writeDailyIfMissing("day " + i);
        }
        List<BackupFile> autos = service.list().stream().filter(f -> f.kind() == Kind.AUTO).toList();
        assertEquals(AutoBackupService.KEEP_AUTO, autos.size());
        assertEquals("otomatis-" + day.plusDays(AutoBackupService.KEEP_AUTO + 5) + ".json", autos.get(0).fileName());
        assertFalse(Files.exists(dir.resolve("backups/otomatis-" + day + ".json")), "the oldest were deleted");
    }

    @Test
    void safetyBackupsAreNeverRotatedAwayAndOtherFilesAreNeverTouched() throws Exception {
        Files.createDirectories(dir.resolve("backups"));
        Files.writeString(dir.resolve("backups/cadangan-saya.json"), "mine");
        Files.writeString(dir.resolve("backups/catatan.txt"), "note");
        for (int i = 0; i < 25; i++) {
            clock.set(LocalDate.of(2026, 11, 21), LocalTime.of(10, 0, i));
            service.writeSafety("s" + i);
        }
        clock.set(LocalDate.of(2026, 11, 21));
        service.writeDailyIfMissing("d");

        assertEquals(25, service.list().stream().filter(f -> f.kind() == Kind.SAFETY).count(), "none of the safety backups was rotated away");
        assertEquals(1, service.list().stream().filter(f -> f.kind() == Kind.AUTO).count(), "daily backups were not pushed out by safety ones");
        assertEquals("mine", Files.readString(dir.resolve("backups/cadangan-saya.json")));
        assertTrue(Files.exists(dir.resolve("backups/catatan.txt")));
        assertEquals(Kind.OTHER, service.list().stream().filter(f -> f.fileName().equals("cadangan-saya.json")).findFirst().orElseThrow().kind());
    }

    @Test
    void aMissingFolderIsAnEmptyList() {
        assertTrue(service.list().isEmpty());
        assertEquals(dir.resolve("backups"), service.folder());
    }

    @Test
    void dailyRotationNeverDeletesSafetyBackupsEvenWhenManyDaysPass() throws Exception {
        Path safety = service.writeSafety("precious", "hapus");
        for (int i = 0; i < 12; i++) {
            clock.set(LocalDate.of(2026, 12, 1).plusDays(i));
            service.writeDailyIfMissing("d" + i);
        }
        assertTrue(Files.exists(safety));
        assertEquals(5, service.list().stream().filter(f -> f.kind() == Kind.AUTO).count());
    }

    @Test
    void ifTheFolderCannotBeCreatedTheWriteFailsWithAClearErrorAndNothingIsLost() throws Exception {
        Files.writeString(dir.resolve("backups"), "a file where the folder must be");
        assertThrows(com.okto.jalokduitnow.exception.StorageException.class, () -> service.writeDailyIfMissing("x"));
        assertThrows(com.okto.jalokduitnow.exception.StorageException.class, () -> service.writeSafety("x", "hapus"));
        assertEquals("a file where the folder must be", Files.readString(dir.resolve("backups")));
    }

    @Test
    void anOldBackupThatCannotBeDeletedDoesNotMakeTheNewBackupFail() throws Exception {
        Path stuck = dir.resolve("backups/otomatis-2026-01-01.json");
        Files.createDirectories(stuck);                                       // a non-empty folder with a backup's name cannot be deleted as a file
        Files.writeString(stuck.resolve("inside.txt"), "x");
        for (int i = 0; i < 7; i++) {
            clock.set(LocalDate.of(2026, 11, 1).plusDays(i));
            assertTrue(service.writeDailyIfMissing("d" + i).isPresent());
        }
        assertTrue(Files.exists(stuck.resolve("inside.txt")), "left alone, retried at the next prune");
    }

    @Test
    void aDailyBackupNeverOverwritesAndLeavesNoTempFiles() throws Exception {
        Files.createDirectories(dir.resolve("backups/otomatis-2026-11-21.json"));   // the day's name is taken by something else
        assertTrue(service.writeDailyIfMissing("x").isEmpty(), "an existing entry is never overwritten");
        try (var s = Files.list(dir.resolve("backups"))) {
            assertTrue(s.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "no temp leftovers");
        }
    }
}
