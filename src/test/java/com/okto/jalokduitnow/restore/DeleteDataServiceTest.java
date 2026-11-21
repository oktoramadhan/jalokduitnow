package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.reminder.NotificationAction;
import com.okto.jalokduitnow.service.*;
import com.okto.jalokduitnow.service.AutoBackupService.Kind;
import com.okto.jalokduitnow.service.DeleteDataService.DeleteResult;
import com.okto.jalokduitnow.storage.BackupDto;
import com.okto.jalokduitnow.storage.FileSwapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Each action removes only what it should, and a failure at any step leaves the data as it was. */
class DeleteDataServiceTest {
    @TempDir Path root;
    RestoreWorld w;
    Path dir;

    @BeforeEach
    void setUp() throws Exception {
        w = RestoreWorld.create(root.resolve("app")).seed(8, "X");
        dir = w.dir;
        // this week's entries (Mon 16 - Fri 20 Nov 2026), so a weekly quest is already satisfied by existing data
        for (int d = 16; d <= 20; d++) {
            w.fx.finance.add(new Expense(LocalDate.of(2026, 11, d), w.fx.expense("Makan"), 2_000));
        }
        // other state that lives outside the core services
        w.notifications.add(AppNotification.create("Budget Makan", "80 persen", Instant.parse("2026-11-20T10:00:00Z"), NotificationAction.OPEN_CATEGORIES, "budget:abc:80"));
        w.notifications.add(AppNotification.create("Jaga streak-mu", "streak", Instant.parse("2026-11-20T11:00:00Z"), NotificationAction.ADD_TRANSACTION, "streak-risk"));
        w.notifications.add(AppNotification.create("Waktunya mencatat", "catat", Instant.parse("2026-11-20T12:00:00Z"), NotificationAction.ADD_TRANSACTION, "daily-log"));
        w.reminderLog.record("budget:abc:80@2026-11", Instant.parse("2026-11-20T10:00:00Z"));
        w.reminderLog.record("streak-risk@2026-11-20", Instant.parse("2026-11-20T11:00:00Z"));
        w.reminderLog.record("daily-log@2026-11-20", Instant.parse("2026-11-20T12:00:00Z"));
        Files.writeString(dir.resolve("settings.properties"), "theme=dark\n");
        // files the app did NOT create must survive everything
        Files.writeString(dir.resolve("catatan-pribadi.txt"), "milikku");
        Files.createDirectories(dir.resolve("foto"));
        Files.writeString(dir.resolve("foto/liburan.txt"), "jangan disentuh");
        Files.createDirectories(dir.resolve("backups"));
        Files.writeString(dir.resolve("backups/cadangan-yang-kusimpan-sendiri.json"), "{}");
        w.autoBackups.writeDailyIfMissing(w.backupJson(true));
        w.flush();
        w.events.clear();
    }

    // ---- helpers ----

    private DeleteResult run(DeleteAction action, boolean alsoBackups) throws Exception {
        return w.deleteService().execute(action, alsoBackups, w.backupJson(true), p -> { });
    }

    private Map<String, String> dataFiles() throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) files.put(dir.relativize(p).toString().replace('\\', '/'), Files.readString(p));
        }
        return files;
    }

    private static boolean noLeftovers(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.noneMatch(p -> p.getFileName().toString().endsWith(".restore-tmp") || p.getFileName().toString().endsWith(".restore-rollback"));
        }
    }

    private Set<String> categoryNames() {
        return w.fx.categories.getAll().stream().map(Category::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    private List<String> columnNames() {
        return w.fx.schema.getSchema().getColumns().stream().map(c -> c.getId() + ":" + c.getName()).toList();
    }

    private void assertForeignFilesUntouched() throws IOException {
        assertEquals("milikku", Files.readString(dir.resolve("catatan-pribadi.txt")));
        assertEquals("jangan disentuh", Files.readString(dir.resolve("foto/liburan.txt")));
        assertEquals("{}", Files.readString(dir.resolve("backups/cadangan-yang-kusimpan-sendiri.json")));
    }

    // =========================================================================
    // 1. delete all transactions
    // =========================================================================

    @Test
    void deletingAllTransactionsRemovesOnlyTransactions() throws Exception {
        Set<String> cats = categoryNames();
        List<String> columns = columnNames();
        var recipients = w.recipients.getAll().stream().map(Recipient::getId).toList();
        var donorCats = w.donors.donorCategoryIds();
        var reminders = w.reminders.current();
        long xp = w.game.profile().getXp();
        int level = w.game.profile().getLevel(), badges = w.game.unlockedCount(), goals = w.game.goals().size();
        assertTrue(w.fx.finance.getAll().size() > 10);

        run(DeleteAction.TRANSACTIONS, false);

        assertTrue(w.fx.finance.getAll().isEmpty());
        assertEquals("[]", Files.readString(dir.resolve("transactions.json")).replaceAll("\\s", ""));
        assertEquals(0, w.fx.finance.totalIncome() + w.fx.finance.totalExpense());
        // everything else is exactly as it was
        assertEquals(cats, categoryNames());
        assertEquals(columns, columnNames());
        assertEquals(recipients, w.recipients.getAll().stream().map(Recipient::getId).toList());
        assertEquals(donorCats, w.donors.donorCategoryIds());
        assertEquals(reminders.dailyLogTime(), w.reminders.current().dailyLogTime());
        assertEquals(reminders.budgetWarnPercent(), w.reminders.current().budgetWarnPercent());
        assertEquals(xp, w.game.profile().getXp());
        assertEquals(level, w.game.profile().getLevel());
        assertEquals(badges, w.game.unlockedCount());
        assertEquals(goals, w.game.goals().size());
        assertForeignFilesUntouched();
        assertEquals("theme=dark\n", Files.readString(dir.resolve("settings.properties")), "settings are kept");
        assertFalse(Files.exists(dir.resolve("transactions.json.bak")), "the previous version of the file holds the deleted data");
        assertTrue(noLeftovers(dir));
    }

    @Test
    void deletingTransactionsAlsoClearsTheNotificationsAboutSpendingAndLetsBudgetRemindersApplyFresh() throws Exception {
        run(DeleteAction.TRANSACTIONS, false);
        assertEquals(List.of("daily-log", "streak-risk"), w.notifications.getAll().stream().map(AppNotification::getReminderId).sorted().toList(),
                "only the budget notification, which describes deleted spending, was removed");
        assertFalse(w.reminderLog.hasFired("budget:abc:80@2026-11"), "so a budget reminder can fire for new spending");
        assertTrue(w.reminderLog.hasFired("daily-log@2026-11-20"));
    }

    // =========================================================================
    // 2. reset game progress
    // =========================================================================

    @Test
    void resettingTheGameClearsOnlyTheGameAndAwardsNothing() throws Exception {
        long txCount = w.fx.finance.getAll().size();
        Set<String> cats = categoryNames();
        List<String> columns = columnNames();
        var recipients = w.recipients.getAll().stream().map(Recipient::getId).toList();
        assertTrue(w.game.profile().getXp() > 0 && w.game.unlockedCount() > 0 && !w.game.goals().isEmpty());

        run(DeleteAction.GAME, false);

        GameProfile p = w.game.profile();
        assertEquals(0, p.getXp());
        assertEquals(1, p.getLevel());
        assertEquals(0, p.getStreak());
        assertEquals(0, p.getLongestStreak());
        assertEquals(0, p.getFreezeTokens());
        assertNull(p.getLastActiveDate());
        assertEquals(0, w.game.unlockedCount(), "no badges, although the data would earn some again");
        assertTrue(w.game.goals().isEmpty(), "savings goals are part of the game progress");
        assertFalse(w.game.quests().isEmpty(), "this week's quests were started");
        assertTrue(w.events.stream().noneMatch(e -> e instanceof GameEvent.XpGained || e instanceof GameEvent.BadgeUnlocked
                || e instanceof GameEvent.LevelUp || e instanceof GameEvent.QuestCompleted || e instanceof GameEvent.StreakChanged),
                "a reset awards nothing: " + w.events);

        assertEquals(txCount, w.fx.finance.getAll().size(), "transactions are kept");
        assertEquals(cats, categoryNames());
        assertEquals(columns, columnNames());
        assertEquals(recipients, w.recipients.getAll().stream().map(Recipient::getId).toList());
        assertEquals(LocalTime.of(18, 30), w.reminders.current().dailyLogTime());
        assertForeignFilesUntouched();
        assertEquals("theme=dark\n", Files.readString(dir.resolve("settings.properties")));
        assertFalse(Files.exists(dir.resolve("game.json.bak")));
        assertTrue(noLeftovers(dir));
    }

    @Test
    void theGameStaysResetAfterARestartAndTheStreakIsNotRebuiltFromHistory() throws Exception {
        run(DeleteAction.GAME, false);
        w.flush();
        RestoreWorld again = w.reopen();
        assertEquals(0, again.game.profile().getXp());
        assertEquals(0, again.game.profile().getStreak(), "the fresh state was written explicitly, so no history replay");
        assertEquals(0, again.game.unlockedCount());
        assertTrue(again.game.goals().isEmpty());
    }

    @Test
    void aQuestThatOldDataAlreadySatisfiesIsNotPaidOutAfterTheReset() throws Exception {
        run(DeleteAction.GAME, false);
        var log = w.game.quests().stream().filter(q -> q.getId().startsWith("log-days")).findFirst().orElseThrow();
        assertTrue(w.game.progressOf(log).isCompleted(), "5 entry days this week already exist");
        assertTrue(w.game.isRewarded(log), "marked as settled silently");

        w.events.clear();
        w.fx.finance.add(new Expense(LocalDate.of(2026, 11, 21), w.fx.expense("Makan"), 1_000));
        assertTrue(w.events.stream().noneMatch(e -> e instanceof GameEvent.XpGained x && "Quest selesai".equals(x.reason())),
                "no quest XP for old data");
        assertTrue(w.events.stream().anyMatch(e -> e instanceof GameEvent.XpGained x && x.amount() == 18), "the normal XP for the new entry");
        assertTrue(w.events.stream().noneMatch(e -> e instanceof GameEvent.QuestCompleted));
    }

    @Test
    void resettingTheGameRemovesTheStreakNotificationButKeepsTheOthers() throws Exception {
        run(DeleteAction.GAME, false);
        assertEquals(List.of("budget:abc:80", "daily-log"), w.notifications.getAll().stream().map(AppNotification::getReminderId).sorted().toList());
        assertFalse(w.reminderLog.hasFired("streak-risk@2026-11-20"));
        assertTrue(w.reminderLog.hasFired("budget:abc:80@2026-11"));
    }

    // =========================================================================
    // 3. factory reset
    // =========================================================================

    @Test
    void aFactoryResetReturnsTheFirstRunDefaults() throws Exception {
        run(DeleteAction.FACTORY, false);

        assertTrue(w.fx.finance.getAll().isEmpty());
        assertEquals(new TreeSet<>(CategoryService.DEFAULT_NAMES), categoryNames(), "the default categories came back, nothing of ours");
        assertEquals(List.of("keterangan:Keterangan"), columnNames(), "only the default column");
        assertTrue(w.recipients.getAll().isEmpty());
        assertTrue(w.donors.donorCategoryIds().isEmpty());
        assertEquals(LocalTime.of(20, 0), w.reminders.current().dailyLogTime());
        assertEquals(80, w.reminders.current().budgetWarnPercent());
        assertEquals(0, w.game.profile().getXp());
        assertEquals(1, w.game.profile().getLevel());
        assertEquals(0, w.game.unlockedCount());
        assertTrue(w.game.goals().isEmpty());
        assertTrue(w.notifications.getAll().isEmpty());
        assertFalse(w.reminderLog.hasFired("daily-log@2026-11-20"));
        assertEquals(DeleteAction.FACTORY, w.lastUiReset, "the view state and theme were reset by the app");
        assertFalse(Files.exists(dir.resolve("settings.properties")), "the theme file is gone");
    }

    @Test
    void afterAFactoryResetANewStartReadsTheSameDefaults() throws Exception {
        run(DeleteAction.FACTORY, false);
        w.flush();
        RestoreWorld fresh = w.reopen();
        assertTrue(fresh.fx.finance.getAll().isEmpty());
        assertEquals(new TreeSet<>(CategoryService.DEFAULT_NAMES), fresh.fx.categories.getAll().stream().map(Category::getName).collect(Collectors.toCollection(TreeSet::new)));
        assertEquals(1, fresh.fx.schema.getSchema().getColumns().size());
        assertEquals(0, fresh.game.profile().getXp());
        assertTrue(fresh.recipients.getAll().isEmpty());
    }

    @Test
    void aFactoryResetLeavesNoTraceOfTheOldDataInTheDataFolder() throws Exception {
        // the data files also have ".bak" copies of their previous versions and a quarantined corrupt copy
        Files.writeString(dir.resolve("categories.json.corrupt-20261101120000.bak"), "old secrets");
        w.fx.finance.add(new Expense(LocalDate.of(2026, 11, 21), w.fx.expense("Makan"), 12_345));
        w.flush();
        assertTrue(Files.exists(dir.resolve("transactions.json.bak")));

        run(DeleteAction.FACTORY, false);
        w.flush();

        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(p);
                String name = p.getFileName().toString();
                if (name.equals("catatan-pribadi.txt")) continue;
                assertFalse(content.contains("12345") || content.contains("old secrets") || content.contains("Donor X"),
                        name + " still has old data");
            }
        }
        assertFalse(Files.exists(dir.resolve("transactions.json.bak")));
        assertFalse(Files.exists(dir.resolve("categories.json.corrupt-20261101120000.bak")));
        assertForeignFilesUntouched();
        assertTrue(noLeftovers(dir));
    }

    @Test
    void existingBackupsAreKeptByDefault() throws Exception {
        List<String> before = w.autoBackups.list().stream().map(f -> f.fileName()).toList();
        assertFalse(before.isEmpty());
        DeleteResult result = run(DeleteAction.FACTORY, false);

        assertEquals(0, result.backupsDeleted());
        for (String name : before) assertTrue(Files.exists(dir.resolve("backups").resolve(name)), name + " must still exist");
        assertTrue(Files.exists(result.safetyBackup()));
        assertForeignFilesUntouched();
    }

    @Test
    void theAlsoDeleteBackupsOptionRemovesOnlyTheAppsOldBackupsAndKeepsTheNewSafetyBackup() throws Exception {
        w.autoBackups.writeSafety(w.backupJson(true));                       // an older safety backup
        List<String> old = w.autoBackups.list().stream().filter(f -> f.kind() != Kind.OTHER).map(f -> f.fileName()).toList();
        assertTrue(old.size() >= 2);

        DeleteResult result = run(DeleteAction.FACTORY, true);

        assertEquals(old.size(), result.backupsDeleted());
        for (String name : old) assertFalse(Files.exists(dir.resolve("backups").resolve(name)), name + " should be deleted");
        assertTrue(Files.exists(result.safetyBackup()), "the backup made by this very action is the way back: it is always kept");
        assertEquals(1, w.autoBackups.list().stream().filter(f -> f.kind() != Kind.OTHER).count());
        assertEquals("{}", Files.readString(dir.resolve("backups/cadangan-yang-kusimpan-sendiri.json")), "the user's own file is not ours to delete");
        assertEquals("milikku", Files.readString(dir.resolve("catatan-pribadi.txt")));
    }

    @Test
    void theAlsoDeleteBackupsOptionDoesNothingForTheOtherActions() throws Exception {
        int before = w.autoBackups.list().size();
        DeleteResult result = run(DeleteAction.TRANSACTIONS, true);
        assertEquals(0, result.backupsDeleted());
        assertTrue(w.autoBackups.list().size() >= before, "backups are only ever deleted by a factory reset");
    }

    // =========================================================================
    // the safety backup
    // =========================================================================

    @Test
    void everyActionMakesAFullSafetyBackupFirstWithAHelpfulName() throws Exception {
        for (DeleteAction action : DeleteAction.values()) {
            RestoreWorld fresh = RestoreWorld.create(root.resolve("safety-" + action)).seed(6, "S");
            int tx = fresh.fx.finance.getAll().size();
            DeleteResult result = fresh.deleteService().execute(action, false, fresh.backupJson(true), p -> { });

            Path file = result.safetyBackup();
            assertTrue(file.getFileName().toString().startsWith("sebelum-hapus-"), file.getFileName().toString());
            assertEquals(fresh.dir.resolve("backups"), file.getParent());
            BackupDto saved = com.okto.jalokduitnow.service.BackupService.parse(Files.readString(file));
            assertEquals(tx, saved.transactions().size(), action + ": the backup holds the data as it was BEFORE the action");
            assertEquals("JalokDuitNow", saved.app());
            assertTrue(fresh.autoBackups.list().stream().anyMatch(f -> f.path().equals(file) && f.kind() == Kind.SAFETY));
        }
    }

    @Test
    void theSafetyBackupRestoresTheDataAfterEachAction() throws Exception {
        for (DeleteAction action : DeleteAction.values()) {
            RestoreWorld fresh = RestoreWorld.create(root.resolve("undo-" + action)).seed(6, "U");
            Set<UUID> txIds = fresh.fx.finance.getAll().stream().map(Transaction::getId).collect(Collectors.toSet());
            Set<String> cats = fresh.fx.categories.getAll().stream().map(c -> c.getId() + c.getName()).collect(Collectors.toSet());
            long xp = fresh.game.profile().getXp();
            int badges = fresh.game.unlockedCount();
            int recipients = fresh.recipients.getAll().size();

            DeleteResult result = fresh.deleteService().execute(action, false, fresh.backupJson(true), p -> { });
            RestoreService restore = fresh.restoreService();
            restore.apply(restore.prepare(result.safetyBackup()), fresh.backupJson(true), p -> { });

            assertEquals(txIds, fresh.fx.finance.getAll().stream().map(Transaction::getId).collect(Collectors.toSet()), action + ": transactions are back");
            assertEquals(cats, fresh.fx.categories.getAll().stream().map(c -> c.getId() + c.getName()).collect(Collectors.toSet()), action + ": categories");
            assertEquals(xp, fresh.game.profile().getXp(), action + ": xp");
            assertEquals(badges, fresh.game.unlockedCount(), action + ": badges");
            assertEquals(recipients, fresh.recipients.getAll().size(), action + ": donors");
            assertEquals(1, fresh.game.goals().size(), action + ": goals");
        }
    }

    @Test
    void theRestoreShortcutIsOfferedForAShortTime() throws Exception {
        DeleteResult result = run(DeleteAction.TRANSACTIONS, false);
        assertEquals(w.clock.instant().plus(DeleteDataService.RESTORE_WINDOW), result.restorableUntil());
        assertEquals(java.time.Duration.ofMinutes(15), DeleteDataService.RESTORE_WINDOW);

        Instant created = w.clock.instant();
        assertTrue(NotificationAction.OPEN_RESTORE.isAvailable(created, created.plusSeconds(14 * 60 + 59)));
        assertFalse(NotificationAction.OPEN_RESTORE.isAvailable(created, created.plusSeconds(15 * 60)), "the window has closed");
        assertFalse(NotificationAction.OPEN_RESTORE.isAvailable(created, created.plusSeconds(3600)));
        assertTrue(NotificationAction.ADD_TRANSACTION.isAvailable(created, created.plusSeconds(86_400)), "other actions never expire");
        assertFalse(NotificationAction.NONE.isAvailable(created, created), "no button at all");
    }

    // =========================================================================
    // the confirmation word
    // =========================================================================

    @Test
    void onlyTheExactWordDeleteConfirms() {
        assertTrue(DeleteDataService.isConfirmed("DELETE"));
        for (String wrong : new String[]{"delete", "Delete", " DELETE", "DELETE ", "DELET", "DELETE!", "HAPUS", "", " ", "D E L E T E", "DELETE\n"}) {
            assertFalse(DeleteDataService.isConfirmed(wrong), "'" + wrong + "'");
        }
        assertFalse(DeleteDataService.isConfirmed(null));
        assertEquals("DELETE", DeleteDataService.CONFIRM_WORD);
    }

    // =========================================================================
    // the counts in the confirmation dialog
    // =========================================================================

    @Test
    void thePreviewStatesExactlyWhatWillBeDeletedWithCounts() throws Exception {
        BackupDto now = w.backup.snapshot(true);
        int tx = now.transactions().size();
        DeletePreview t = DeletePreview.of(DeleteAction.TRANSACTIONS, now, 3, 2);
        assertEquals(List.of(tx + " transaksi"), t.willDelete());
        assertTrue(t.kept().get(0).contains("kolom kustom (1)"), t.kept().toString());

        DeletePreview g = DeletePreview.of(DeleteAction.GAME, now, 3, 2);
        String all = String.join(" | ", g.willDelete());
        assertTrue(all.contains("Lv " + w.game.profile().getLevel()) && all.contains(w.game.profile().getXp() + " XP"), all);
        assertTrue(all.contains(w.game.unlockedCount() + " badge") && all.contains("1 target tabungan"), all);
        assertTrue(g.kept().get(0).contains(tx + " transaksi"));

        DeletePreview f = DeletePreview.of(DeleteAction.FACTORY, now, 3, 2);
        String fa = String.join(" | ", f.willDelete());
        assertTrue(fa.contains(tx + " transaksi, 1 kolom kustom, " + now.categories().size() + " kategori"), fa);
        assertTrue(fa.contains("3 notifikasi"));
        assertEquals(2, f.backupFiles());
    }

    @Test
    void theBackupFileCountIsShownForTheFactoryResetOption() throws Exception {
        assertEquals(1, w.deleteService().backupFileCount(), "only the app's own backups are counted, not the user's file");
        w.autoBackups.writeSafety("x", "hapus");
        assertEquals(2, w.deleteService().backupFileCount());
    }

    // =========================================================================
    // a failure leaves everything as it was
    // =========================================================================

    private void assertEveryFailureStepIsRolledBack(DeleteAction action, boolean alsoBackups) throws Exception {
        boolean succeeded = false;
        for (int failAt = 1; failAt <= 60 && !succeeded; failAt++) {
            Map<String, String> before = dataFiles();
            Set<UUID> txBefore = w.fx.finance.getAll().stream().map(Transaction::getId).collect(Collectors.toSet());
            int notificationsBefore = w.notifications.getAll().size();
            long xpBefore = w.game.profile().getXp();

            AtomicInteger calls = new AtomicInteger();
            FileSwapper.Mover real = FileSwapper.retryingMover(1, 0);
            int fail = failAt;
            DeleteDataService service = w.deleteService(new FileSwapper((from, to) -> {
                if (calls.incrementAndGet() == fail) throw new IOException("kegagalan buatan pada langkah " + fail);
                real.move(from, to);
            }), Runnable::run, w.reloader);

            String snapshot = w.backupJson(true);
            try {
                service.execute(action, alsoBackups, snapshot, p -> { });
                succeeded = true;                                           // there were fewer moves than failAt: the action ran fully
            } catch (StorageException e) {
                // the safety backup is the one allowed difference: it only ADDS a file
                Map<String, String> after = dataFiles();
                after.keySet().removeIf(k -> k.startsWith("backups/sebelum-hapus-"));
                Map<String, String> beforeWithoutSafety = new TreeMap<>(before);
                beforeWithoutSafety.keySet().removeIf(k -> k.startsWith("backups/sebelum-hapus-"));
                assertEquals(beforeWithoutSafety, after, action + ": data files after a failure at move " + failAt + " (" + e.getMessage() + ")");
                assertEquals(txBefore, w.fx.finance.getAll().stream().map(Transaction::getId).collect(Collectors.toSet()), action + ": app data, move " + failAt);
                assertEquals(notificationsBefore, w.notifications.getAll().size(), action + ": notifications, move " + failAt);
                assertEquals(xpBefore, w.game.profile().getXp());
                assertTrue(noLeftovers(dir), action + ": no leftovers after move " + failAt);
            }
        }
        assertTrue(succeeded, action + ": the loop must reach a run that needs no more failing steps");
    }

    @Test
    void aFailureAtAnyStepOfDeletingTransactionsChangesNothing() throws Exception {
        assertEveryFailureStepIsRolledBack(DeleteAction.TRANSACTIONS, false);
    }

    @Test
    void aFailureAtAnyStepOfAResetGameChangesNothing() throws Exception {
        assertEveryFailureStepIsRolledBack(DeleteAction.GAME, false);
    }

    @Test
    void aFailureAtAnyStepOfAFactoryResetChangesNothing() throws Exception {
        assertEveryFailureStepIsRolledBack(DeleteAction.FACTORY, true);
    }

    @Test
    void aFailureWhileReloadingTheAppBringsTheOldFilesAndTheOldStateBack() throws Exception {
        for (DeleteAction action : DeleteAction.values()) {
            RestoreWorld fresh = RestoreWorld.create(root.resolve("reload-" + action)).seed(5, "R");
            Map<String, String> before = new TreeMap<>();
            fresh.flush();
            try (Stream<Path> s = Files.list(fresh.dir)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) before.put(p.getFileName().toString(), Files.readString(p));
            }
            int tx = fresh.fx.finance.getAll().size();
            AtomicInteger reloads = new AtomicInteger();
            RestoreService.Reloader flaky = () -> {
                if (reloads.incrementAndGet() == 1) throw new IllegalStateException("gagal memuat (uji)");
                fresh.reloader.reloadAll();
            };
            DeleteDataService service = fresh.deleteService(new FileSwapper(FileSwapper.retryingMover(1, 0)), Runnable::run, flaky);

            StorageException e = assertThrows(StorageException.class, () -> service.execute(action, false, fresh.backupJson(true), p -> { }));
            assertTrue(e.getMessage().contains("dikembalikan"), e.getMessage());
            assertEquals(2, reloads.get(), action + ": reloaded again from the old files");
            assertEquals(tx, fresh.fx.finance.getAll().size(), action + ": the running app shows the old data");
            Map<String, String> after = new TreeMap<>();
            try (Stream<Path> s = Files.list(fresh.dir)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) after.put(p.getFileName().toString(), Files.readString(p));
            }
            assertEquals(before, after, action + ": data files are back to what they were");
            assertTrue(noLeftovers(fresh.dir));
        }
    }

    @Test
    void ifTheSafetyBackupCannotBeWrittenNothingIsDeleted() throws Exception {
        Files.delete(dir.resolve("backups/cadangan-yang-kusimpan-sendiri.json"));
        for (Path p : w.autoBackups.list().stream().map(f -> f.path()).toList()) Files.delete(p);
        Files.delete(dir.resolve("backups"));
        Files.writeString(dir.resolve("backups"), "a file where the backups folder must be");   // the folder cannot be created
        Map<String, String> before = dataFiles();
        for (DeleteAction action : DeleteAction.values()) {
            assertThrows(StorageException.class, () -> run(action, false), action.toString());
            assertEquals(before, dataFiles(), action + ": nothing was deleted without a safety backup");
        }
        assertFalse(w.fx.finance.getAll().isEmpty());
    }

    // =========================================================================
    // unsafe paths
    // =========================================================================

    @Test
    void aSymbolicLinkInsideTheDataFolderIsNeverFollowedOrDeleted() throws Exception {
        Path outside = root.resolve("precious-outside-file.json");
        Files.writeString(outside, "do not touch");
        Path linked = dir.resolve("recipients.json");
        Files.delete(linked);
        try {
            Files.createSymbolicLink(linked, outside);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }
        Map<String, String> others = dataFiles();
        others.remove("recipients.json");

        StorageException e = assertThrows(StorageException.class, () -> run(DeleteAction.FACTORY, true));
        assertTrue(e.getMessage().contains("symbolic link"), e.getMessage());
        assertEquals("do not touch", Files.readString(outside), "the file outside the data folder is untouched");
        assertTrue(Files.isSymbolicLink(linked), "the link itself is untouched too");
        Map<String, String> after = dataFiles();
        after.remove("recipients.json");
        after.keySet().removeIf(k -> k.startsWith("backups/sebelum-hapus-"));
        others.keySet().removeIf(k -> k.startsWith("backups/sebelum-hapus-"));
        assertEquals(others, after, "no other file was deleted either");
    }

    @Test
    void aBackupsFolderThatIsASymbolicLinkIsNeverWrittenToOrPruned() throws Exception {
        Path outsideDir = Files.createDirectories(root.resolve("outside-dir"));
        Files.writeString(outsideDir.resolve("otomatis-2020-01-01.json"), "outside backup");
        Files.writeString(outsideDir.resolve("catatan.txt"), "outside");
        Path backups = dir.resolve("backups");
        for (Path p : w.autoBackups.list().stream().map(f -> f.path()).toList()) Files.delete(p);
        try (Stream<Path> rest = Files.list(backups)) { for (Path p : rest.toList()) Files.delete(p); }
        Files.delete(backups);
        try {
            Files.createSymbolicLink(backups, outsideDir);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }
        assertThrows(StorageException.class, () -> run(DeleteAction.FACTORY, true));
        assertTrue(Files.exists(outsideDir.resolve("otomatis-2020-01-01.json")), "nothing outside was deleted");
        assertEquals("outside", Files.readString(outsideDir.resolve("catatan.txt")));
        try (Stream<Path> s = Files.list(outsideDir)) {
            assertEquals(2, s.count(), "and nothing was written there");
        }
        assertFalse(w.fx.finance.getAll().isEmpty(), "the data is untouched");
    }

    @Test
    void theServiceRefusesToWorkOnAnUnsafeDataFolder() {
        assertThrows(StorageException.class, () -> new DeleteDataService(Path.of(System.getProperty("user.home")), w.autoBackups,
                new FileSwapper(), Runnable::run, w.reloader, new DeletePostActions(w.notifications, w.reminderLog, w.game, a -> { }), w.clock));
        assertThrows(StorageException.class, () -> new DeleteDataService(Path.of("/"), w.autoBackups,
                new FileSwapper(), Runnable::run, w.reloader, new DeletePostActions(w.notifications, w.reminderLog, w.game, a -> { }), w.clock));
    }

    // =========================================================================
    // threading, progress, pending saves
    // =========================================================================

    @Test
    void progressGoesFromZeroToOneAndNeverBackwards() throws Exception {
        List<Double> seen = new ArrayList<>();
        w.deleteService().execute(DeleteAction.TRANSACTIONS, false, w.backupJson(true), seen::add);
        assertEquals(1.0, seen.get(seen.size() - 1));
        for (int i = 1; i < seen.size(); i++) assertTrue(seen.get(i) >= seen.get(i - 1), seen.toString());
        assertTrue(seen.get(0) < 0.5 && seen.size() >= 4);
    }

    @Test
    void theReloadRunsOnTheUiThreadWhileTheDeletionRunsInTheBackgroundWithoutDeadlock() throws Exception {
        ExecutorService fakeFx = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-fx"));
        ExecutorService background = Executors.newSingleThreadExecutor(r -> new Thread(r, "background-delete"));
        try {
            AtomicReference<String> reloadThread = new AtomicReference<>();
            DeleteDataService service = w.deleteService(new FileSwapper(FileSwapper.retryingMover(1, 0)), fakeFx,
                    () -> { reloadThread.set(Thread.currentThread().getName()); w.reloader.reloadAll(); });
            String snapshot = w.backupJson(true);
            Future<DeleteResult> result = background.submit(() -> service.execute(DeleteAction.FACTORY, false, snapshot, p -> { }));
            assertNotNull(result.get(20, TimeUnit.SECONDS));
            assertEquals("fake-fx", reloadThread.get());
        } finally {
            fakeFx.shutdownNow();
            background.shutdownNow();
        }
    }

    @Test
    void everyServiceTellsItsListenersSoEveryViewRefreshes() throws Exception {
        int[] heard = new int[5];
        w.fx.finance.addListener(() -> heard[0]++);
        w.fx.categories.addListener(() -> heard[1]++);
        w.game.addListener(() -> heard[2]++);
        w.recipients.addListener(() -> heard[3]++);
        w.notifications.addListener(() -> heard[4]++);
        run(DeleteAction.FACTORY, false);
        for (int i = 0; i < heard.length; i++) assertTrue(heard[i] > 0, "listener group " + i + " was not told");
        assertEquals(1, w.uiResets);
    }

    @Test
    void aSaveThatWasStillWaitingNeverBringsTheDeletedDataBack() throws Exception {
        DeleteDataService service = w.deleteService();
        String snapshot = w.backupJson(true);
        w.fx.finance.add(new Expense(LocalDate.of(2026, 11, 21), w.fx.expense("Makan"), 98_765));   // its save is still counting down
        service.execute(DeleteAction.TRANSACTIONS, false, snapshot, p -> { });

        Thread.sleep(800);
        RestoreWorld fresh = w.reopen();
        assertTrue(fresh.fx.finance.getAll().isEmpty(), "the old pending snapshot was discarded");
    }

    @Test
    void runningAnActionTwiceIsHarmless() throws Exception {
        run(DeleteAction.TRANSACTIONS, false);
        run(DeleteAction.TRANSACTIONS, false);
        run(DeleteAction.GAME, false);
        run(DeleteAction.GAME, false);
        run(DeleteAction.FACTORY, false);
        run(DeleteAction.FACTORY, false);
        assertTrue(w.fx.finance.getAll().isEmpty());
        assertEquals(0, w.game.profile().getXp());
        assertEquals(6, w.autoBackups.list().stream().filter(f -> f.kind() == Kind.SAFETY).count(), "each run made its own safety backup");
    }
}
