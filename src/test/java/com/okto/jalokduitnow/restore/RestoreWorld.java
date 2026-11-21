package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.ChoiceType;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.service.*;
import com.okto.jalokduitnow.storage.*;
import com.okto.jalokduitnow.storage.FileSwapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Every service on one data folder, wired like the app (a "world"). Tests build two worlds and restore one into the other. */
final class RestoreWorld {
    final Path dir;
    final TestClock clock;
    final Fixture fx;
    final GameEventBus bus = new GameEventBus();
    final List<GameEvent> events = new ArrayList<>();
    final GameService game;
    final RecipientService recipients;
    final DonorSettings donors;
    final ReminderSettingsService reminders;
    final BackupService backup;
    final AutoBackupService autoBackups;
    final DataReloader reloader;
    final NotificationCenter notifications;
    final ReminderLog reminderLog;
    int uiResets;
    DeleteAction lastUiReset;

    private RestoreWorld(Path dir, LocalDate today) throws Exception {
        this.dir = dir;
        this.clock = new TestClock(today);
        this.fx = Fixture.in(dir, clock);
        fx.finance.setEventBus(bus);
        recipients = new RecipientService(new JsonRepository<>(dir.resolve("recipients.json"), RecipientDto.class));
        donors = new DonorSettings(new JsonRepository<>(dir.resolve("donor-settings.json"), DonorSettingsDto.class));
        reminders = new ReminderSettingsService(new JsonRepository<>(dir.resolve("reminders.json"), ReminderSettingsDto.class));
        recipients.load();
        donors.load();
        reminders.load();
        game = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx.finance, fx.categories, fx.schema, bus, new XpPolicy(), clock);
        game.load();
        bus.subscribe(GameEvent.class, events::add);
        backup = new BackupService(fx.finance, fx.schema, fx.categories, game, recipients, donors, reminders, clock);
        autoBackups = new AutoBackupService(dir.resolve("backups"), clock);
        reloader = new DataReloader(fx.finance, fx.schema, fx.categories, game, recipients, donors, reminders);
        notifications = new NotificationCenter(new JsonRepository<>(dir.resolve("notifications.json"), NotificationDto.class));
        reminderLog = new ReminderLog(new JsonRepository<>(dir.resolve("reminder-log.json"), ReminderLogDto.class));
        notifications.load();
        reminderLog.load();
    }

    static RestoreWorld create(Path dir) throws Exception {
        return new RestoreWorld(dir, LocalDate.of(2026, 11, 21));
    }

    RestoreService restoreService() {
        return restoreService(new FileSwapper(FileSwapper.retryingMover(1, 0)), Runnable::run, reloader);
    }

    RestoreService restoreService(FileSwapper swapper, Executor ui, RestoreService.Reloader reload) {
        return new RestoreService(dir, autoBackups, swapper, ui, reload);
    }

    /** Fills the world with a recognisable set of data. */
    RestoreWorld seed(int transactions, String marker) throws Exception {
        UUID food = fx.expense("Makan");
        UUID coffee = fx.expense("Kopi " + marker);
        UUID salary = fx.income("Gaji");
        ColumnDefinition kind = fx.schema.addColumn("Jenis " + marker, new ChoiceType(List.of("Need", "Want")), false);
        for (int i = 0; i < transactions; i++) {
            Transaction t = new Expense(LocalDate.of(2026, 11, 1 + i % 20), i % 2 == 0 ? food : coffee, 1_000L * (i + 1));
            t.setCustomValue(kind, i % 2 == 0 ? "Need" : "Want");
            fx.finance.add(t);
        }
        fx.finance.add(new Income(LocalDate.of(2026, 11, 1), salary, 5_000_000));
        recipients.add("Donor " + marker, "0812-3456-78" + (marker.length() % 10) + "0", "catatan " + marker, true);
        donors.setDonorCategory(salary, true);
        try {
            reminders.setDailyLogTime(java.time.LocalTime.of(18, 30));
            reminders.setBudgetWarnPercent(70);
        } catch (com.okto.jalokduitnow.exception.InvalidSettingException e) {
            throw new IllegalStateException(e);
        }
        game.addGoal("Target " + marker, 1_000_000, fx.expense("Tabungan"));
        flush();
        return this;
    }

    DeleteDataService deleteService() throws com.okto.jalokduitnow.exception.StorageException {
        return deleteService(new FileSwapper(FileSwapper.retryingMover(1, 0)), Runnable::run, reloader);
    }

    DeleteDataService deleteService(FileSwapper swapper, Executor ui, RestoreService.Reloader reload)
            throws com.okto.jalokduitnow.exception.StorageException {
        return new DeleteDataService(dir, autoBackups, swapper, ui, reload,
                new DeletePostActions(notifications, reminderLog, game, action -> { uiResets++; lastUiReset = action; }), clock);
    }

    void flush() {
        notifications.flush();
        reminderLog.flush();
        fx.flush();
        game.flush();
        recipients.flush();
        donors.flush();
        reminders.flush();
    }

    String backupJson(boolean includeDonors) throws Exception {
        flush();
        return backup.backupJson(includeDonors);
    }

    /** A fresh set of services reading the files on disk (what the app would show after a restart). */
    RestoreWorld reopen() throws Exception {
        flush();
        return new RestoreWorld(dir, clock.today());
    }
}
