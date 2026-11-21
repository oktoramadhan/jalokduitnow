package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.ExportFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The automatic backups: full backup files (the same format as a manual one) in the "backups" folder of the data
 * directory. Two kinds live there:
 * <ul>
 *   <li>a daily backup, made once per day when the app starts (the last {@value #KEEP_AUTO} are kept);</li>
 *   <li>a safety backup made right before a restore replaces the data (the last {@value #KEEP_SAFETY} are kept).</li>
 * </ul>
 * Either can be chosen later in the restore dialog. This class only manages files; it never reads app data itself:
 * the caller passes the backup text.
 */
public class AutoBackupService {
    public static final int KEEP_AUTO = 14;
    public static final int KEEP_SAFETY = 10;
    static final String AUTO_PREFIX = "otomatis-";
    static final String SAFETY_PREFIX = "sebelum-";           // "sebelum-pemulihan-..." and "sebelum-hapus-..."
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public enum Kind { AUTO, SAFETY, OTHER }

    /** One backup file in the folder. */
    public record BackupFile(Path path, Kind kind, Instant modified, long size) {
        public String fileName() { return path.getFileName().toString(); }
    }

    private final Path folder;
    private final Clock clock;

    public AutoBackupService(Path folder, Clock clock) {
        this.folder = folder;
        this.clock = clock;
    }

    public Path folder() {
        return folder;
    }

    /** Writes today's automatic backup unless one already exists. @return the file if one was written */
    public Optional<Path> writeDailyIfMissing(String backupJson) throws StorageException {
        Path file = folder.resolve(AUTO_PREFIX + LocalDate.now(clock) + ".json");
        if (Files.exists(file)) return Optional.empty();
        write(file, backupJson);
        prune();
        return Optional.of(file);
    }

    /** Writes a safety backup of the data as it is right now (called before a restore). @return the file written */
    public Path writeSafety(String backupJson) throws StorageException {
        return writeSafety(backupJson, "pemulihan");
    }

    /**
     * Writes a safety backup named "sebelum-<reason>-<time>.json" (reason: "pemulihan" before a restore, "hapus" before data
     * is deleted). Both kinds are listed together and can be restored like any backup.
     */
    public Path writeSafety(String backupJson, String reason) throws StorageException {
        String stamp = LocalDateTime.now(clock).format(STAMP);
        String base = SAFETY_PREFIX + reason + "-" + stamp;
        Path file = folder.resolve(base + ".json");
        for (int n = 2; Files.exists(file); n++) file = folder.resolve(base + "-" + n + ".json");
        write(file, backupJson);
        prune();
        return file;
    }

    /** All backups in the folder, newest first. */
    public List<BackupFile> list() {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(this::describe)
                    .sorted(Comparator.comparing(BackupFile::modified).reversed().thenComparing(BackupFile::fileName, Comparator.reverseOrder()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    // ---- internals ----

    private void write(Path file, String json) throws StorageException {
        // a "backups" folder that is a symbolic link could lead anywhere: never write or prune through it
        if (Files.isSymbolicLink(folder)) {
            throw new StorageException("Folder cadangan adalah symbolic link dan tidak dipakai. Hapus link itu agar cadangan bisa dibuat.", null);
        }
        ExportFile.write(file, json, false);
        try {
            Files.setLastModifiedTime(file, FileTime.from(clock.instant()));   // ordering follows the app's clock
        } catch (IOException ignored) {
            // the real modification time is a fine fallback
        }
    }

    private BackupFile describe(Path p) {
        String name = p.getFileName().toString();
        Kind kind = name.startsWith(AUTO_PREFIX) ? Kind.AUTO : name.startsWith(SAFETY_PREFIX) ? Kind.SAFETY : Kind.OTHER;
        try {
            return new BackupFile(p, kind, Files.getLastModifiedTime(p).toInstant(), Files.size(p));
        } catch (IOException e) {
            return new BackupFile(p, kind, Instant.EPOCH, 0);
        }
    }

    /** Keeps the newest KEEP_AUTO daily and KEEP_SAFETY safety backups; deletes older ones. Other files are never touched. */
    private void prune() {
        prune(Kind.AUTO, KEEP_AUTO);
        prune(Kind.SAFETY, KEEP_SAFETY);
    }

    private void prune(Kind kind, int keep) {
        if (Files.isSymbolicLink(folder)) return;
        List<BackupFile> ofKind = list().stream().filter(f -> f.kind() == kind).toList();   // newest first
        for (int i = keep; i < ofKind.size(); i++) {
            try {
                Files.deleteIfExists(ofKind.get(i).path());
            } catch (IOException ignored) {
                // a file that cannot be deleted now is retried at the next prune
            }
        }
    }
}
