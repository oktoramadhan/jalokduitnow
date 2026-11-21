package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.storage.FileSwapper;
import com.okto.jalokduitnow.storage.GameStateDto;
import com.okto.jalokduitnow.storage.JsonSupport;
import com.okto.jalokduitnow.storage.SafeDataFolder;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.DoubleConsumer;

/**
 * Deletes data on request, safely: delete all transactions, reset the game, or a factory reset.
 *
 * <p>Every action follows the same steps (the same safety net a restore uses):
 * <ol>
 *   <li><b>Plan.</b> Which files change is decided up front and every path is checked by {@link SafeDataFolder}: only files the
 *       app created, only inside its data folder, no symbolic links. An unsafe path stops the action before anything happens.</li>
 *   <li><b>Safety backup.</b> A full backup of the current data is written first ("sebelum-hapus-...json"). If that fails,
 *       nothing is deleted.</li>
 *   <li><b>Atomic change.</b> The files are replaced or moved aside as one unit while auto-saves are held back; a failure
 *       puts every file back. Deleted files only really disappear at the very end ({@code commit}).</li>
 *   <li><b>Reload.</b> The running app reloads from the files on the UI thread, with rewards suspended, so nothing earns XP or
 *       a badge. If the reload fails, the old files come back and the app is reloaded from them.</li>
 * </ol>
 * A deletion of transactions or of the game also removes the ".bak" copy of that file, because that copy holds the data that
 * was just deleted. The safety backup is the one deliberate exception and is shown to the user.
 */
public class DeleteDataService {
    /** The word the user must type before the confirm button works. */
    public static final String CONFIRM_WORD = "DELETE";
    /** How long the "restore it" shortcut stays offered. */
    public static final Duration RESTORE_WINDOW = Duration.ofMinutes(15);

    /** Hooks the app provides for what lives outside the core services (notifications, reminder log, theme, view state). */
    public interface PostActions {
        /** Reloads notifications and the reminder log from disk. Runs after the core reload, and again after a rollback. */
        void reloadAuxiliary() throws Exception;

        /** Cleans up what no longer applies after the action succeeded (stale notifications, view state...). Best effort. */
        void after(DeleteAction action) throws Exception;
    }

    /** @param safetyBackup the backup made first (restorable); @param restorableUntil end of the "restore it" shortcut */
    public record DeleteResult(DeleteAction action, Path safetyBackup, Instant restorableUntil, int backupsDeleted) {}

    private final SafeDataFolder folder;
    private final AutoBackupService autoBackups;
    private final FileSwapper swapper;
    private final Executor uiExecutor;
    private final RestoreService.Reloader reloader;
    private final PostActions post;
    private final Clock clock;

    public DeleteDataService(Path dataDir, AutoBackupService autoBackups, FileSwapper swapper, Executor uiExecutor,
                             RestoreService.Reloader reloader, PostActions post, Clock clock) throws StorageException {
        this.folder = new SafeDataFolder(dataDir);
        this.autoBackups = autoBackups;
        this.swapper = swapper;
        this.uiExecutor = uiExecutor;
        this.reloader = reloader;
        this.post = post;
        this.clock = clock;
    }

    /** True only for exactly "DELETE" (upper case, nothing around it). */
    public static boolean isConfirmed(String typed) {
        return CONFIRM_WORD.equals(typed);
    }

    /** How many automatic and safety backups exist (shown next to the factory reset's "also delete backups" option). */
    public int backupFileCount() throws StorageException {
        return folder.existingBackupFiles().size();
    }

    // =========================================================================
    // execute
    // =========================================================================

    /**
     * Runs one action. Call from a background thread; the reload is handed to the UI executor.
     *
     * @param alsoDeleteBackups only used by FACTORY: also delete the automatic and safety backups that exist now
     *                          (the safety backup made by this very action is always kept)
     * @param currentBackupJson a full backup of the data as it is NOW, taken on the UI thread by the caller
     * @throws StorageException if anything fails; the data is then exactly as before
     */
    public DeleteResult execute(DeleteAction action, boolean alsoDeleteBackups, String currentBackupJson, DoubleConsumer progress)
            throws StorageException {
        progress.accept(0.05);
        // 1. decide and validate everything before touching anything
        Plan plan = plan(action, alsoDeleteBackups);
        progress.accept(0.15);

        // 2. the safety backup: if it cannot be written, nothing is deleted
        Path safety = autoBackups.writeSafety(currentBackupJson, "hapus");
        progress.accept(0.3);

        // 3. change the files as one unit, with auto-saves held back
        FileSwapper.Swap swap;
        try {
            swap = AutoSaveService.exclusively(() -> swapper.swap(folder.dataDir(), plan.contents(), plan.deletions()));
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Gagal menghapus data: " + e.getMessage(), e);
        }
        progress.accept(0.6);

        // 4. reload the running app; if that fails the old files come back
        try {
            runOnUi(() -> {
                reloader.reloadAll();
                post.reloadAuxiliary();
            });
        } catch (StorageException failure) {
            rollBack(swap, failure);
            throw new StorageException("Penghapusan dibatalkan karena aplikasi tidak bisa memuat ulang datanya (" + failure.getMessage()
                    + "). Data sebelumnya sudah dikembalikan.", failure);
        }
        swap.commit();
        progress.accept(0.9);

        try {                                                               // clean-up is a courtesy: it never undoes the deletion
            runOnUi(() -> post.after(action));
        } catch (StorageException e) {
            System.err.println("Pembersihan setelah penghapusan gagal: " + e.getMessage());
        }
        progress.accept(1.0);
        return new DeleteResult(action, safety, Instant.now(clock).plus(RESTORE_WINDOW), plan.backupsDeleted());
    }

    // =========================================================================
    // planning
    // =========================================================================

    /** What one action will write and delete. */
    private record Plan(Map<String, String> contents, List<String> deletions, int backupsDeleted) {}

    private Plan plan(DeleteAction action, boolean alsoDeleteBackups) throws StorageException {
        Map<String, String> contents = new LinkedHashMap<>();
        Set<String> deletions = new LinkedHashSet<>();
        int backupsDeleted = 0;

        switch (action) {
            case TRANSACTIONS -> {
                contents.put("transactions.json", "[]");
                deletions.add("transactions.json.bak");               // the copy of the previous version holds the deleted data
            }
            case GAME -> {
                contents.put("game.json", freshGameState());
                deletions.add("game.json.bak");
            }
            case FACTORY -> {
                deletions.addAll(folder.existingDataFiles());          // every file the app created (validated, symlinks refused)
                if (alsoDeleteBackups) {
                    List<String> old = folder.existingBackupFiles();
                    deletions.addAll(old);
                    backupsDeleted = old.size();
                }
            }
        }
        for (String name : contents.keySet()) folder.resolveOwned(name);
        for (String name : deletions) folder.resolveOwned(name);       // refuses anything that is not ours or not safe
        return new Plan(contents, List.copyOf(deletions), backupsDeleted);
    }

    /** A brand-new game state: level 1, no streak, no badges, no quests, no goals. Written explicitly so nothing is rebuilt from history. */
    private static String freshGameState() throws StorageException {
        try {
            GameStateDto fresh = new GameStateDto(GameStateDto.ProfileDto.from(new GameProfile()), List.of(), List.of(),
                    Map.of(), List.of(), false, null);
            return JsonSupport.newMapper().writeValueAsString(List.of(fresh));
        } catch (IOException e) {
            throw new StorageException("Gagal menyiapkan keadaan game baru", e);
        }
    }

    // =========================================================================
    // rollback and UI hand-off
    // =========================================================================

    private void rollBack(FileSwapper.Swap swap, StorageException cause) throws StorageException {
        try {
            AutoSaveService.exclusively(() -> {
                swap.rollback();
                return null;
            });
            runOnUi(() -> {
                reloader.reloadAll();
                post.reloadAuxiliary();
            });
        } catch (StorageException e) {
            e.addSuppressed(cause);
            throw e;
        } catch (Exception e) {
            throw new StorageException("Data sebelumnya tidak bisa dikembalikan otomatis: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface UiStep { void run() throws Exception; }

    private void runOnUi(UiStep step) throws StorageException {
        CompletableFuture<Void> done = new CompletableFuture<>();
        uiExecutor.execute(() -> {
            try {
                step.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            done.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new StorageException(String.valueOf(cause.getMessage()), cause);
        } catch (TimeoutException e) {
            throw new StorageException("Aplikasi tidak merespons saat memuat ulang data", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("Penghapusan dihentikan", e);
        }
    }

    /** Names of the files a factory reset would delete (for the dialog and for tests). */
    public List<String> factoryResetFiles() throws StorageException {
        return folder.existingDataFiles();
    }
}
