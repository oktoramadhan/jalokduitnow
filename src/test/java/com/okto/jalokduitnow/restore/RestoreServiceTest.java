package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.service.AutoBackupService.BackupFile;
import com.okto.jalokduitnow.service.AutoBackupService.Kind;
import com.okto.jalokduitnow.service.RestoreService;
import com.okto.jalokduitnow.service.RestoreService.RestorePlan;
import com.okto.jalokduitnow.service.RestoreService.RestoreResult;
import com.okto.jalokduitnow.storage.BackupDto;
import com.okto.jalokduitnow.storage.FileSwapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two worlds: "source" is where the backup comes from, "target" is the running app that gets restored.
 * They use different data folders and different data, so a restore is clearly visible.
 */
class RestoreServiceTest {
    @TempDir Path root;
    RestoreWorld source;
    RestoreWorld target;
    Path backupFile;

    @BeforeEach
    void setUp() throws Exception {
        source = RestoreWorld.create(root.resolve("source")).seed(8, "SRC");
        target = RestoreWorld.create(root.resolve("target")).seed(3, "OLD");
        backupFile = root.resolve("cadangan-sumber.json");
        Files.writeString(backupFile, source.backupJson(true), StandardCharsets.UTF_8);
    }

    // ---- helpers ----

    /** Every data file in the target folder and its exact bytes (the backups folder is not data). */
    private Map<String, String> dataFiles() throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> s = Files.list(target.dir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) files.put(p.getFileName().toString(), Files.readString(p));
        }
        return files;
    }

    private List<String> amountsOf(RestoreWorld w) {
        return w.fx.finance.getAll().stream().map(t -> t.typeLabel() + t.getAmount() + t.getDate()).sorted().toList();
    }

    private Set<UUID> idsOf(RestoreWorld w) {
        return w.fx.finance.getAll().stream().map(Transaction::getId).collect(Collectors.toSet());
    }

    private static boolean noLeftovers(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.noneMatch(p -> p.getFileName().toString().endsWith(".restore-tmp") || p.getFileName().toString().endsWith(".restore-rollback"));
        }
    }

    // =========================================================================
    // the happy path
    // =========================================================================

    @Test
    void aValidBackupReplacesEverythingAndTheAppShowsIt() throws Exception {
        RestoreService service = target.restoreService();
        RestorePlan plan = service.prepare(backupFile);
        RestoreResult result = service.apply(plan, target.backupJson(true), p -> { });

        // transactions: the very same ones, with ids, amounts, categories and custom values
        assertEquals(idsOf(source), idsOf(target));
        assertEquals(9, target.fx.finance.getAll().size());
        for (Transaction s : source.fx.finance.getAll()) {
            Transaction t = target.fx.finance.getAll().stream().filter(x -> x.getId().equals(s.getId())).findFirst().orElseThrow();
            assertEquals(s.getAmount(), t.getAmount());
            assertEquals(s.getDate(), t.getDate());
            assertEquals(s.getCategoryId(), t.getCategoryId());
            assertEquals(s.getCustomValues(), t.getCustomValues());
            assertEquals(s.getCreatedAt(), t.getCreatedAt());
        }
        // categories and schema
        assertEquals(source.fx.categories.getAll().stream().map(c -> c.getId() + c.getName()).collect(Collectors.toSet()),
                target.fx.categories.getAll().stream().map(c -> c.getId() + c.getName()).collect(Collectors.toSet()));
        assertEquals(source.fx.schema.getSchema().getColumns().stream().map(c -> c.getId() + c.getName()).toList(),
                target.fx.schema.getSchema().getColumns().stream().map(c -> c.getId() + c.getName()).toList());
        assertTrue(target.fx.categories.getAll().stream().noneMatch(c -> c.getName().contains("OLD")), "the old data is gone");
        // game, goals, donors and reminders
        assertEquals(source.game.profile().getXp(), target.game.profile().getXp());
        assertEquals(source.game.goals().stream().map(g -> g.name()).toList(), target.game.goals().stream().map(g -> g.name()).toList());
        assertEquals(source.game.unlockedCount(), target.game.unlockedCount());
        assertEquals(source.recipients.getAll().stream().map(r -> r.getId() + r.getPhoneNumber()).toList(),
                target.recipients.getAll().stream().map(r -> r.getId() + r.getPhoneNumber()).toList());
        assertEquals(source.donors.donorCategoryIds(), target.donors.donorCategoryIds());
        assertEquals(source.reminders.current().dailyLogTime(), target.reminders.current().dailyLogTime());
        assertEquals(70, target.reminders.current().budgetWarnPercent());
        assertEquals(LocalTime.of(18, 30), target.reminders.current().dailyLogTime());

        assertNotNull(result.safetyBackup());
        assertTrue(noLeftovers(target.dir), "no temporary or rollback files remain");
    }

    @Test
    void whatIsOnDiskAfterTheRestoreIsWhatAFreshStartWouldLoad() throws Exception {
        RestoreService service = target.restoreService();
        service.apply(service.prepare(backupFile), target.backupJson(true), p -> { });
        target.flush();

        RestoreWorld restarted = target.reopen();                       // new services reading the files
        assertEquals(idsOf(source), idsOf(restarted));
        assertEquals(source.game.profile().getXp(), restarted.game.profile().getXp());
        assertEquals(source.fx.categories.getAll().size(), restarted.fx.categories.getAll().size());
        assertEquals(1, restarted.recipients.getAll().size());
    }

    @Test
    void thePreviewShowsTheBackupsNumbersAndTheCurrentOnesCanBeComparedWithIt() throws Exception {
        RestorePlan plan = target.restoreService().prepare(backupFile);
        var preview = plan.preview();
        assertEquals(9, preview.transactions());
        assertEquals(1, preview.customColumns(), "the default text column is not counted as a custom one");
        assertEquals(source.fx.categories.getAll().size(), preview.categories());
        assertEquals(source.game.unlockedCount(), preview.badges());
        assertEquals(source.game.profile().getLevel(), preview.level());
        assertEquals(source.game.profile().getXp(), preview.xp());
        assertEquals(1, preview.goals());
        assertEquals(1, preview.recipients());
        assertEquals("0.1.0", preview.appVersion());
        assertEquals(1, preview.formatVersion());
        assertEquals(source.clock.instant(), preview.exportedAt());
        assertTrue(preview.hasDonorData());
        assertTrue(preview.hasReminderSettings());

        var current = com.okto.jalokduitnow.service.BackupPreview.of(target.backup.snapshot(true));
        assertEquals(4, current.transactions(), "current: 3 + 1 income, backup: 9");
        assertNotEquals(current.transactions(), preview.transactions());
    }

    @Test
    void progressGoesFromZeroToOneAndNeverBackwards() throws Exception {
        List<Double> seen = new ArrayList<>();
        RestoreService service = target.restoreService();
        service.apply(service.prepare(backupFile), target.backupJson(true), seen::add);
        assertTrue(seen.size() >= 3);
        assertEquals(1.0, seen.get(seen.size() - 1));
        for (int i = 1; i < seen.size(); i++) assertTrue(seen.get(i) >= seen.get(i - 1), "progress " + seen);
        assertTrue(seen.get(0) < 0.5);
    }

    @Test
    void restoringTwiceGivesTheSameResult() throws Exception {
        RestoreService service = target.restoreService();
        RestorePlan plan = service.prepare(backupFile);
        service.apply(plan, target.backupJson(true), p -> { });
        service.apply(plan, target.backupJson(true), p -> { });
        assertEquals(idsOf(source), idsOf(target));
        assertEquals(2, target.autoBackups.list().stream().filter(f -> f.kind() == Kind.SAFETY).count(), "each restore made its own safety backup");
    }

    @Test
    void aByteOrderMarkInTheBackupFileIsTolerated() throws Exception {
        Path withBom = root.resolve("bom.json");
        byte[] body = Files.readAllBytes(backupFile);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xEF; bytes[1] = (byte) 0xBB; bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        Files.write(withBom, bytes);
        assertEquals(9, target.restoreService().prepare(withBom).preview().transactions());
    }

    // =========================================================================
    // a bad file leaves everything untouched
    // =========================================================================

    @Test
    void aCorruptFileIsRefusedAndNothingChanges() throws Exception {
        Map<String, String> before = dataFiles();
        List<String> txBefore = amountsOf(target);
        RestoreService service = target.restoreService();

        List<byte[]> bad = new ArrayList<>();
        bad.add(new byte[0]);
        bad.add("not json at all".getBytes(StandardCharsets.UTF_8));
        bad.add("{ \"app\": \"JalokDuitNow\", ".getBytes(StandardCharsets.UTF_8));
        bad.add(new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, (byte) 0x80, (byte) 0x81});            // not UTF-8 at all
        String good = Files.readString(backupFile);
        bad.add(good.substring(0, good.length() / 2).getBytes(StandardCharsets.UTF_8));            // cut in half
        for (byte[] content : bad) {
            Path file = root.resolve("bad.json");
            Files.write(file, content);
            StorageException e = assertThrows(StorageException.class, () -> service.prepare(file));
            assertFalse(e.getMessage().isBlank());
        }
        assertThrows(StorageException.class, () -> service.prepare(root.resolve("does-not-exist.json")));
        assertThrows(StorageException.class, () -> service.prepare(root));                           // a folder, not a file

        assertEquals(before, dataFiles(), "not one byte of the data files changed");
        assertEquals(txBefore, amountsOf(target));
        assertFalse(Files.exists(target.dir.resolve("backups")), "no safety backup either: prepare changes nothing");
        assertTrue(noLeftovers(target.dir));
    }

    @Test
    void aBackupFromANewerVersionIsRefusedAndNothingChanges() throws Exception {
        Map<String, String> before = dataFiles();
        Path newer = root.resolve("newer.json");
        Files.writeString(newer, Files.readString(backupFile).replace("\"formatVersion\" : 1", "\"formatVersion\" : 2"));
        StorageException e = assertThrows(StorageException.class, () -> target.restoreService().prepare(newer));
        assertTrue(e.getMessage().contains("lebih baru"), e.getMessage());
        assertEquals(before, dataFiles());
    }

    @Test
    void aBackupMissingASectionIsRefusedAndNothingChanges() throws Exception {
        Map<String, String> before = dataFiles();
        Path incomplete = root.resolve("incomplete.json");
        var mapper = com.okto.jalokduitnow.storage.JsonSupport.newMapper();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Files.readString(backupFile));
        tree.remove("categories");
        Files.writeString(incomplete, mapper.writeValueAsString(tree));
        StorageException e = assertThrows(StorageException.class, () -> target.restoreService().prepare(incomplete));
        assertTrue(e.getMessage().contains("kategori"), e.getMessage());
        assertEquals(before, dataFiles());
    }

    @Test
    void aBackupWithABrokenTransactionIsFoundDuringPrepareNotMidRestore() throws Exception {
        Map<String, String> before = dataFiles();
        Path broken = root.resolve("broken.json");
        var mapper = com.okto.jalokduitnow.storage.JsonSupport.newMapper();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Files.readString(backupFile));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("transactions").get(2)).put("categoryId", UUID.randomUUID().toString());
        Files.writeString(broken, mapper.writeValueAsString(tree));
        assertThrows(StorageException.class, () -> target.restoreService().prepare(broken));
        assertEquals(before, dataFiles());
    }

    // =========================================================================
    // a failure in the middle rolls everything back
    // =========================================================================

    @Test
    void aFailureAtEveryPossibleStepOfTheSwapLeavesTheOldDataExactlyAsItWas() throws Exception {
        RestorePlan plan = target.restoreService().prepare(backupFile);
        int moves = plan.files().size() * 2;                              // at most: set aside + put in place, per file
        for (int failAt = 1; failAt <= moves; failAt++) {
            Map<String, String> before = dataFiles();
            List<String> txBefore = amountsOf(target);
            AtomicInteger calls = new AtomicInteger();
            FileSwapper.Mover real = FileSwapper.retryingMover(1, 0);
            int fail = failAt;
            RestoreService service = target.restoreService(new FileSwapper((from, to) -> {
                if (calls.incrementAndGet() == fail) throw new IOException("kegagalan buatan pada langkah " + fail);
                real.move(from, to);
            }), Runnable::run, target.reloader);

            StorageException e = assertThrows(StorageException.class, () -> service.apply(plan, target.backupJson(true), p -> { }), "step " + failAt);
            assertTrue(e.getMessage().contains("Tidak ada data yang berubah") || e.getMessage().contains("dikembalikan"), e.getMessage());
            assertEquals(before, dataFiles(), "the data files after a failure at step " + failAt);
            assertEquals(txBefore, amountsOf(target), "the app's data after a failure at step " + failAt);
            assertTrue(noLeftovers(target.dir), "no leftovers after a failure at step " + failAt);
        }
    }

    @Test
    void aFailureWhileReloadingTheAppRestoresTheOldFilesAndReloadsThemAgain() throws Exception {
        Map<String, String> before = dataFiles();
        List<String> txBefore = amountsOf(target);
        AtomicInteger reloads = new AtomicInteger();
        RestoreService.Reloader flaky = () -> {
            if (reloads.incrementAndGet() == 1) throw new IllegalStateException("data baru tidak bisa dimuat (uji)");
            target.reloader.reloadAll();
        };
        RestoreService service = target.restoreService(new FileSwapper(FileSwapper.retryingMover(1, 0)), Runnable::run, flaky);

        StorageException e = assertThrows(StorageException.class,
                () -> service.apply(service.prepare(backupFile), target.backupJson(true), p -> { }));
        assertTrue(e.getMessage().contains("dikembalikan"), e.getMessage());
        assertEquals(2, reloads.get(), "the app was reloaded a second time from the old files");
        assertEquals(before, dataFiles());
        assertEquals(txBefore, amountsOf(target), "the running app shows the old data again");
        assertTrue(noLeftovers(target.dir));
    }

    @Test
    void aFailureToWriteTheSafetyBackupStopsBeforeAnythingIsReplaced() throws Exception {
        Map<String, String> before = dataFiles();
        Files.writeString(target.dir.resolve("backups"), "a file where the backups folder should be");   // the folder cannot be created
        RestoreService service = target.restoreService();
        RestorePlan plan = service.prepare(backupFile);
        assertThrows(StorageException.class, () -> service.apply(plan, target.backupJson(true), p -> { }));
        Files.delete(target.dir.resolve("backups"));
        assertEquals(before, dataFiles(), "without a safety backup the data is not touched");
    }

    // =========================================================================
    // safety backup and automatic backups
    // =========================================================================

    @Test
    void aSafetyBackupOfTheOldDataIsMadeFirstAndCanUndoTheRestore() throws Exception {
        List<String> oldTx = amountsOf(target);
        RestoreService service = target.restoreService();
        RestoreResult result = service.apply(service.prepare(backupFile), target.backupJson(true), p -> { });

        Path safety = result.safetyBackup();
        assertTrue(safety.getFileName().toString().startsWith("sebelum-pemulihan-"));
        assertEquals(target.dir.resolve("backups"), safety.getParent());
        assertTrue(Files.exists(safety));
        BackupDto saved = com.okto.jalokduitnow.service.BackupService.parse(Files.readString(safety));
        assertEquals(4, saved.transactions().size(), "it holds the data as it was BEFORE the restore");
        assertTrue(target.autoBackups.list().stream().anyMatch(f -> f.kind() == Kind.SAFETY && f.path().equals(safety)));

        assertEquals(9, target.fx.finance.getAll().size());
        RestorePlan undo = service.prepare(safety);                       // restoring the safety backup returns the old data
        service.apply(undo, target.backupJson(true), p -> { });
        assertEquals(oldTx, amountsOf(target));
        assertTrue(target.fx.categories.getAll().stream().anyMatch(c -> c.getName().contains("OLD")));
    }

    @Test
    void theSafetyBackupIsWrittenBeforeTheFilesAreSwapped() throws Exception {
        AtomicReference<Boolean> existedAtFirstMove = new AtomicReference<>();
        FileSwapper.Mover real = FileSwapper.retryingMover(1, 0);
        RestoreService service = target.restoreService(new FileSwapper((from, to) -> {
            if (existedAtFirstMove.get() == null) {
                existedAtFirstMove.set(!target.autoBackups.list().isEmpty());
            }
            real.move(from, to);
        }), Runnable::run, target.reloader);
        service.apply(service.prepare(backupFile), target.backupJson(true), p -> { });
        assertEquals(Boolean.TRUE, existedAtFirstMove.get());
    }

    @Test
    void youCanRestoreFromAnAutomaticBackupWithoutAFileChooser() throws Exception {
        source.autoBackups.writeDailyIfMissing(source.backupJson(true));
        Path auto = source.autoBackups.list().get(0).path();
        // the user's target app sees the automatic backups in its own folder: copy it there like a file in that folder
        Files.createDirectories(target.dir.resolve("backups"));
        Files.copy(auto, target.dir.resolve("backups").resolve(auto.getFileName()));

        List<BackupFile> listed = target.autoBackups.list();
        assertEquals(1, listed.size());
        assertEquals(Kind.AUTO, listed.get(0).kind());

        RestoreService service = target.restoreService();
        service.apply(service.prepare(listed.get(0).path()), target.backupJson(true), p -> { });
        assertEquals(idsOf(source), idsOf(target));
        assertEquals(2, target.autoBackups.list().size(), "the automatic backup is kept, and a safety backup was added");
    }

    // =========================================================================
    // the UI refresh hook
    // =========================================================================

    @Test
    void theReloadHookRunsOnceOnTheUiExecutorAndEveryServiceTellsItsListeners() throws Exception {
        int[] heard = new int[6];
        target.fx.finance.addListener(() -> heard[0]++);
        target.fx.schema.addListener(() -> heard[1]++);
        target.fx.categories.addListener(() -> heard[2]++);
        target.game.addListener(() -> heard[3]++);
        target.recipients.addListener(() -> heard[4]++);
        target.reminders.addListener(() -> heard[5]++);

        AtomicInteger hookCalls = new AtomicInteger();
        RestoreService service = target.restoreService(new FileSwapper(FileSwapper.retryingMover(1, 0)), Runnable::run,
                () -> { hookCalls.incrementAndGet(); target.reloader.reloadAll(); });
        service.apply(service.prepare(backupFile), target.backupJson(true), p -> { });

        assertEquals(1, hookCalls.get());
        for (int i = 0; i < heard.length; i++) assertTrue(heard[i] > 0, "listener group " + i + " was not told, so its view would not refresh");
    }

    @Test
    void theReloadRunsOnTheUiThreadWhileApplyRunsOnAnotherAndDoesNotDeadlock() throws Exception {
        ExecutorService fakeFx = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-fx"));
        ExecutorService background = Executors.newSingleThreadExecutor(r -> new Thread(r, "background-restore"));
        try {
            AtomicReference<String> reloadThread = new AtomicReference<>();
            AtomicReference<String> applyThread = new AtomicReference<>();
            RestoreService service = target.restoreService(new FileSwapper(FileSwapper.retryingMover(1, 0)), fakeFx,
                    () -> { reloadThread.set(Thread.currentThread().getName()); target.reloader.reloadAll(); });
            RestorePlan plan = service.prepare(backupFile);
            String safety = target.backupJson(true);

            Future<RestoreResult> result = background.submit(() -> {
                applyThread.set(Thread.currentThread().getName());
                return service.apply(plan, safety, p -> { });
            });
            assertNotNull(result.get(20, TimeUnit.SECONDS));
            assertEquals("fake-fx", reloadThread.get());
            assertEquals("background-restore", applyThread.get());
        } finally {
            fakeFx.shutdownNow();
            background.shutdownNow();
        }
    }

    // =========================================================================
    // restored data never earns anything; things that are not in the backup stay
    // =========================================================================

    @Test
    void restoredDataDoesNotTriggerXpOrBadges() throws Exception {
        // a source whose data would unlock badges (custom columns and categories) but whose game state says "nothing yet"
        RestoreWorld plain = RestoreWorld.create(root.resolve("plain"));
        plain.fx.schema.addColumn("A", new com.okto.jalokduitnow.schema.TextType(), false);
        plain.fx.schema.addColumn("B", new com.okto.jalokduitnow.schema.TextType(), false);
        plain.fx.categories.add("Satu", com.okto.jalokduitnow.model.CategoryType.EXPENSE, "food", "#112233", null);
        plain.fx.categories.add("Dua", com.okto.jalokduitnow.model.CategoryType.EXPENSE, "food", "#112233", null);
        plain.fx.categories.add("Tiga", com.okto.jalokduitnow.model.CategoryType.EXPENSE, "food", "#112233", null);
        plain.game.reset();                                               // xp 0, no badges, although the conditions are true
        assertEquals(0, plain.game.profile().getXp());
        assertEquals(0, plain.game.unlockedCount());
        Path file = root.resolve("plain-backup.json");
        Files.writeString(file, plain.backupJson(true));

        target.events.clear();
        RestoreService service = target.restoreService();
        service.apply(service.prepare(file), target.backupJson(true), p -> { });

        assertEquals(0, target.game.profile().getXp(), "XP is exactly what the backup says");
        assertEquals(0, target.game.unlockedCount(), "no badge was unlocked by loading restored columns and categories");
        assertTrue(target.events.stream().noneMatch(e -> e instanceof GameEvent.XpGained || e instanceof GameEvent.BadgeUnlocked
                || e instanceof GameEvent.LevelUp || e instanceof GameEvent.QuestCompleted), "no game event was published: " + target.events);

        // and the suspension ended: normal play awards again
        target.fx.finance.add(new com.okto.jalokduitnow.model.Expense(java.time.LocalDate.of(2026, 11, 21), target.fx.expense("Makan"), 1_000));
        assertTrue(target.game.profile().getXp() > 0, "rewards work again after the restore");
    }

    @Test
    void restoredBadgesAndXpAreKeptAsTheyWere() throws Exception {
        long xp = source.game.profile().getXp();
        int badges = source.game.unlockedCount();
        assertTrue(xp > 0 && badges > 0, "the source has earned something");
        RestoreService service = target.restoreService();
        service.apply(service.prepare(backupFile), target.backupJson(true), p -> { });
        assertEquals(xp, target.game.profile().getXp());
        assertEquals(badges, target.game.unlockedCount());
        assertEquals(source.game.profile().getLevel(), target.game.profile().getLevel());
    }

    @Test
    void aBackupMadeWithoutDonorDataKeepsTheCurrentDonors() throws Exception {
        Path noDonors = root.resolve("no-donors.json");
        Files.writeString(noDonors, source.backupJson(false));
        var currentDonors = target.recipients.getAll().stream().map(r -> r.getId() + r.getName()).toList();
        var currentDonorCategories = target.donors.donorCategoryIds();
        String recipientsFileBefore = Files.readString(target.dir.resolve("recipients.json"));

        RestorePlan plan = target.restoreService().prepare(noDonors);
        assertFalse(plan.preview().hasDonorData());
        assertFalse(plan.files().containsKey("recipients.json"));
        target.restoreService().apply(plan, target.backupJson(true), p -> { });

        assertEquals(idsOf(source), idsOf(target), "everything else was restored");
        assertEquals(currentDonors, target.recipients.getAll().stream().map(r -> r.getId() + r.getName()).toList(), "donors are not erased");
        assertEquals(currentDonorCategories, target.donors.donorCategoryIds());
        assertEquals(recipientsFileBefore, Files.readString(target.dir.resolve("recipients.json")));
    }

    @Test
    void aBackupWithoutReminderSettingsKeepsTheCurrentOnes() throws Exception {
        var mapper = com.okto.jalokduitnow.storage.JsonSupport.newMapper();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Files.readString(backupFile));
        tree.remove("reminderSettings");
        Path older = root.resolve("older.json");
        Files.writeString(older, mapper.writeValueAsString(tree));
        target.reminders.setBudgetWarnPercent(55);
        target.flush();

        RestorePlan plan = target.restoreService().prepare(older);
        assertFalse(plan.files().containsKey("reminders.json"));
        target.restoreService().apply(plan, target.backupJson(true), p -> { });
        assertEquals(55, target.reminders.current().budgetWarnPercent());
        assertEquals(idsOf(source), idsOf(target));
    }

    // =========================================================================
    // a save that was waiting must not land on top of the restored files
    // =========================================================================

    @Test
    void aSaveThatWasStillWaitingNeverOverwritesTheRestoredFiles() throws Exception {
        RestoreService service = target.restoreService();
        RestorePlan plan = service.prepare(backupFile);
        String safety = target.backupJson(true);                          // the snapshot (this flushes everything that was pending)

        // an edit made AFTER the snapshot: its auto-save is still counting down its short delay when the swap happens
        target.fx.finance.add(new com.okto.jalokduitnow.model.Expense(java.time.LocalDate.of(2026, 11, 20), target.fx.expense("Makan"), 777));
        service.apply(plan, safety, p -> { });

        Thread.sleep(800);                                                // longer than the auto-save delay
        RestoreWorld fresh = target.reopen();
        assertEquals(9, fresh.fx.finance.getAll().size(), "the file still holds the restored data");
        assertEquals(idsOf(source), idsOf(fresh));
        assertTrue(fresh.fx.finance.getAll().stream().noneMatch(t -> t.getAmount() == 777), "the old pending save was discarded");
    }
}
