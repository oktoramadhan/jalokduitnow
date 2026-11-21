package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.*;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.DoubleConsumer;

/**
 * Restores the app's data from a backup file, safely.
 *
 * <p><b>Step 1, {@link #prepare}: read and validate. Changes nothing.</b> The file must be a JalokDuitNow backup of a version this
 * build knows, with every required section, and everything in it must load (BackupValidator). The result is a plan:
 * a preview and the exact text of every data file to write.
 *
 * <p><b>Step 2, {@link #apply}: replace.</b>
 * <ol>
 *   <li>A safety backup of the current data is written first (if that fails, nothing else happens).</li>
 *   <li>All new files are written next to the old ones and swapped in as one unit while auto-saves are held back
 *       (FileSwapper). A failure here puts every file back.</li>
 *   <li>The running app is reloaded from the new files through the {@link Reloader}, on the UI thread. If that fails, the old
 *       files are restored and the app reloaded again, so the user is never left with a half-restored state.</li>
 * </ol>
 * Restored game data is applied as it was saved: the reloader suspends rewards (see DataReloader), so a restore never
 * gives XP or badges.
 *
 * <p>Sections a backup does not carry (donors when they were left out, reminder settings in an older backup) are not
 * written, so the current ones stay.
 */
public class RestoreService {
    static final long MAX_BYTES = 50L * 1024 * 1024;

    /** Reloads the running app from the data files. Runs on the UI executor. */
    @FunctionalInterface
    public interface Reloader {
        void reloadAll() throws Exception;
    }

    /** A validated backup and what applying it would write. Nothing has been changed yet. */
    public record RestorePlan(Path source, BackupDto backup, BackupPreview preview, Map<String, String> files) {}

    /** The outcome of a successful restore. */
    public record RestoreResult(Path safetyBackup, BackupPreview restored) {}

    private final Path dataDir;
    private final AutoBackupService autoBackups;
    private final FileSwapper swapper;
    private final Executor uiExecutor;
    private final Reloader reloader;

    public RestoreService(Path dataDir, AutoBackupService autoBackups, FileSwapper swapper, Executor uiExecutor, Reloader reloader) {
        this.dataDir = dataDir;
        this.autoBackups = autoBackups;
        this.swapper = swapper;
        this.uiExecutor = uiExecutor;
        this.reloader = reloader;
    }

    // =========================================================================
    // step 1: prepare (read-only)
    // =========================================================================

    public RestorePlan prepare(Path file) throws StorageException {
        String json = read(file);
        BackupDto backup = BackupValidator.validate(json);
        return new RestorePlan(file, backup, BackupPreview.of(backup), filesFor(backup));
    }

    private static String read(Path file) throws StorageException {
        try {
            if (!Files.isRegularFile(file)) throw new StorageException("File cadangan tidak ditemukan: " + file.getFileName(), null);
            if (Files.size(file) > MAX_BYTES) throw new StorageException("File terlalu besar untuk sebuah cadangan JalokDuitNow", null);
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return text.startsWith("﻿") ? text.substring(1) : text;          // tolerate a byte order mark
        } catch (CharacterCodingException e) {
            throw new StorageException("File ini bukan teks yang valid, jadi bukan cadangan JalokDuitNow", e);
        } catch (IOException e) {
            throw new StorageException("File cadangan tidak bisa dibaca: " + e.getMessage(), e);
        }
    }

    /** The data files a backup would write, in the app's own file formats. */
    private static Map<String, String> filesFor(BackupDto b) throws StorageException {
        Map<String, String> files = new LinkedHashMap<>();
        try {
            var mapper = JsonSupport.newMapper();
            files.put("schema.json", mapper.writeValueAsString(b.columns()));
            files.put("categories.json", mapper.writeValueAsString(b.categories()));
            files.put("transactions.json", mapper.writeValueAsString(b.transactions()));
            files.put("game.json", mapper.writeValueAsString(List.of(b.game())));
            if (b.hasDonorData()) {
                files.put("recipients.json", mapper.writeValueAsString(b.recipients()));
                files.put("donor-settings.json", mapper.writeValueAsString(List.of(new DonorSettingsDto(
                        b.donorCategoryIds() == null ? List.of() : b.donorCategoryIds()))));
            }
            if (b.reminderSettings() != null) files.put("reminders.json", mapper.writeValueAsString(List.of(b.reminderSettings())));
        } catch (IOException e) {
            throw new StorageException("Gagal menyiapkan data pemulihan", e);
        }
        return files;
    }

    // =========================================================================
    // step 2: apply
    // =========================================================================

    /**
     * Replaces the current data with the plan. Call from a background thread; the reload is handed to the UI executor.
     *
     * @param currentBackupJson a full backup of the data as it is NOW (taken on the UI thread by the caller); it is written
     *                          as the safety backup before anything is replaced
     * @param progress          receives 0.0 - 1.0
     * @throws StorageException if anything fails; the data is then exactly as before
     */
    public RestoreResult apply(RestorePlan plan, String currentBackupJson, DoubleConsumer progress) throws StorageException {
        progress.accept(0.05);
        Path safety = autoBackups.writeSafety(currentBackupJson);          // 1. nothing else happens if this fails
        progress.accept(0.25);

        FileSwapper.Swap swap;
        try {                                                               // 2. swap the files with auto-saves held back
            swap = AutoSaveService.exclusively(() -> swapper.swap(dataDir, plan.files()));
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Gagal mengganti data: " + e.getMessage(), e);
        }
        progress.accept(0.6);

        try {                                                               // 3. reload the running app
            runOnUi();
        } catch (StorageException failure) {
            rollBack(swap, failure);                                        // never leave a half-restored state
            throw new StorageException("Pemulihan dibatalkan karena data baru tidak bisa dimuat (" + failure.getMessage()
                    + "). Data sebelumnya sudah dikembalikan.", failure);
        }
        swap.commit();
        progress.accept(1.0);
        return new RestoreResult(safety, plan.preview());
    }

    /** Puts the old files back and reloads the app from them. */
    private void rollBack(FileSwapper.Swap swap, StorageException cause) throws StorageException {
        try {
            AutoSaveService.exclusively(() -> {
                swap.rollback();
                return null;
            });
            runOnUi();
        } catch (StorageException e) {
            e.addSuppressed(cause);
            throw e;
        } catch (Exception e) {
            throw new StorageException("Data sebelumnya tidak bisa dikembalikan otomatis: " + e.getMessage(), e);
        }
    }

    /** Runs the reloader on the UI executor and waits for it. Never holds a lock while waiting. */
    private void runOnUi() throws StorageException {
        CompletableFuture<Void> done = new CompletableFuture<>();
        uiExecutor.execute(() -> {
            try {
                reloader.reloadAll();
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
            throw new StorageException("Pemulihan dihentikan", e);
        }
    }
}
