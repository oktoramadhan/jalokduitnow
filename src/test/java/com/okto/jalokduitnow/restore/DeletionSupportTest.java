package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.reminder.NotificationAction;
import com.okto.jalokduitnow.service.AutoBackupService;
import com.okto.jalokduitnow.service.NotificationCenter;
import com.okto.jalokduitnow.service.ReminderLog;
import com.okto.jalokduitnow.storage.FileSwapper;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.NotificationDto;
import com.okto.jalokduitnow.storage.ReminderLogDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The small building blocks the delete feature relies on. */
class DeletionSupportTest {
    @TempDir Path dir;

    // ---- FileSwapper: deletions and unsafe names ----

    @Test
    void swapCanDeleteFilesAndCommitRemovesThemForGood() throws Exception {
        Files.writeString(dir.resolve("a.json"), "A");
        Files.writeString(dir.resolve("b.json"), "B");
        FileSwapper.Swap swap = new FileSwapper().swap(dir, Map.of("a.json", "A2"), List.of("b.json", "missing.json"));
        assertEquals("A2", Files.readString(dir.resolve("a.json")));
        assertFalse(Files.exists(dir.resolve("b.json")));
        swap.commit();
        try (var s = Files.list(dir)) { assertEquals(1, s.count(), "no rollback leftovers"); }
    }

    @Test
    void rollbackBringsDeletedFilesBack() throws Exception {
        Files.writeString(dir.resolve("a.json"), "A");
        Files.writeString(dir.resolve("b.json"), "B");
        FileSwapper.Swap swap = new FileSwapper().swap(dir, Map.of(), List.of("a.json", "b.json"));
        assertFalse(Files.exists(dir.resolve("a.json")));
        swap.rollback();
        assertEquals("A", Files.readString(dir.resolve("a.json")));
        assertEquals("B", Files.readString(dir.resolve("b.json")));
    }

    @Test
    void aFailingDeletionRestoresEverythingAlreadyDone() throws Exception {
        for (String n : List.of("a.json", "b.json", "c.json")) Files.writeString(dir.resolve(n), n);
        AtomicInteger calls = new AtomicInteger();
        FileSwapper swapper = new FileSwapper((from, to) -> {
            if (calls.incrementAndGet() == 2) throw new IOException("terkunci (uji)");
            Files.move(from, to);
        });
        assertThrows(StorageException.class, () -> swapper.swap(dir, Map.of(), List.of("a.json", "b.json", "c.json")));
        for (String n : List.of("a.json", "b.json", "c.json")) assertEquals(n, Files.readString(dir.resolve(n)));
        try (var s = Files.list(dir)) { assertEquals(3, s.count()); }
    }

    @Test
    void namesThatEscapeTheFolderAreRefusedBeforeAnythingHappens() throws Exception {
        Path outside = dir.resolveSibling(dir.getFileName() + "-outside.json");
        Files.writeString(outside, "keep");
        Files.writeString(dir.resolve("a.json"), "A");
        for (String bad : List.of("../" + outside.getFileName(), outside.toString(), "", "sub/../../x.json")) {
            assertThrows(StorageException.class, () -> new FileSwapper().swap(dir, Map.of(), List.of("a.json", bad)), bad);
            assertThrows(StorageException.class, () -> new FileSwapper().swap(dir, Map.of(bad, "x")), bad);
        }
        assertEquals("keep", Files.readString(outside));
        assertEquals("A", Files.readString(dir.resolve("a.json")), "the valid name before the bad one was not touched");
        Files.delete(outside);
    }

    // ---- AutoBackupService ----

    @Test
    void safetyBackupsCarryTheReasonInTheirName() throws Exception {
        AutoBackupService service = new AutoBackupService(dir.resolve("backups"), new TestClock(LocalDate.of(2026, 11, 21)));
        assertTrue(service.writeSafety("{}").getFileName().toString().startsWith("sebelum-pemulihan-"));
        Path del = service.writeSafety("{}", "hapus");
        assertTrue(del.getFileName().toString().startsWith("sebelum-hapus-"));
        assertEquals(2, service.list().stream().filter(f -> f.kind() == AutoBackupService.Kind.SAFETY).count());
    }

    @Test
    void aBackupsFolderThatIsASymbolicLinkIsNotWrittenTo() throws Exception {
        Path target = Files.createDirectories(dir.resolve("elsewhere"));
        Path link = dir.resolve("backups");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }
        AutoBackupService service = new AutoBackupService(link, new TestClock(LocalDate.of(2026, 11, 21)));
        assertThrows(StorageException.class, () -> service.writeSafety("{}", "hapus"));
        try (var s = Files.list(target)) { assertEquals(0, s.count()); }
    }

    // ---- NotificationCenter and ReminderLog ----

    @Test
    void notificationsCanBeRemovedByConditionAndReloadedFromDisk() throws Exception {
        Path file = dir.resolve("notifications.json");
        NotificationCenter center = new NotificationCenter(new JsonRepository<>(file, NotificationDto.class));
        center.load();
        Instant t = Instant.parse("2026-11-21T10:00:00Z");
        center.add(AppNotification.create("A", "a", t, NotificationAction.NONE, "budget:x"));
        center.add(AppNotification.create("B", "b", t, NotificationAction.NONE, "daily-log"));
        center.flush();
        int[] heard = {0};
        center.addListener(() -> heard[0]++);

        center.removeIf(n -> n.getReminderId() != null && n.getReminderId().startsWith("budget:"));
        assertEquals(List.of("daily-log"), center.getAll().stream().map(AppNotification::getReminderId).toList());
        assertEquals(1, heard[0]);
        center.removeIf(n -> false);
        assertEquals(1, heard[0], "nothing removed, nobody told");

        center.flush();
        Files.delete(file);
        center.reload();
        assertTrue(center.getAll().isEmpty(), "a missing file means an empty list");
        assertEquals(2, heard[0]);
    }

    @Test
    void theReminderLogForgetsByPrefixAndReloads() throws Exception {
        Path file = dir.resolve("reminder-log.json");
        ReminderLog log = new ReminderLog(new JsonRepository<>(file, ReminderLogDto.class));
        log.load();
        Instant t = Instant.parse("2026-11-21T10:00:00Z");
        log.record("budget:a:80@2026-11", t);
        log.record("budget:b:80@2026-11", t);
        log.record("daily-log@2026-11-21", t);
        log.forgetPrefix("budget:");
        assertFalse(log.hasFired("budget:a:80@2026-11"));
        assertFalse(log.hasFired("budget:b:80@2026-11"));
        assertTrue(log.hasFired("daily-log@2026-11-21"));
        log.flush();

        ReminderLog other = new ReminderLog(new JsonRepository<>(file, ReminderLogDto.class));
        other.load();
        assertEquals(1, other.size(), "the forgetting was saved");
        Files.delete(file);
        log.reload();
        assertEquals(0, log.size());
    }
}
