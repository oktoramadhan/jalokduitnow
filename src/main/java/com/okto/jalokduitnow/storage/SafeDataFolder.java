package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The only thing allowed to decide which files the app may delete. Everything is checked before anything is touched:
 * <ul>
 *   <li>The data folder must not be a file-system root or the user's home folder, and must not itself be a symbolic link.</li>
 *   <li>Only files the app creates are ever listed: the known data files (and their ".bak", ".tmp" and ".corrupt-" copies) in the
 *       data folder, and in the "backups" folder only the automatic and safety backups. Other files, such as a backup the user
 *       copied there or anything unrelated, are never listed, so they can never be deleted.</li>
 *   <li>A name is only accepted if it is exactly such a file: no separators, no "..", not absolute.</li>
 *   <li>A listed file that is a symbolic link is refused (a link is not followed and not trusted), and so is a "backups" folder
 *       that is a link.</li>
 * </ul>
 * The deletion itself is done elsewhere (FileSwapper); this class only answers "which files, and are they safe".
 */
public final class SafeDataFolder {
    /** Files the app writes in its data folder. */
    private static final List<String> DATA_FILES = List.of("transactions.json", "schema.json", "categories.json", "game.json",
            "recipients.json", "donor-settings.json", "reminders.json", "reminder-log.json", "notifications.json");
    /** The theme choice (a Java properties file, not JSON). */
    private static final String SETTINGS_FILE = "settings.properties";
    private static final Pattern CORRUPT_COPY = Pattern.compile(
            "^(transactions|schema|categories|game|recipients|donor-settings|reminders|reminder-log|notifications)\\.json\\.corrupt-[0-9]{8,14}(-[0-9]+)?\\.bak$");
    private static final Pattern APP_BACKUP = Pattern.compile("^(otomatis|sebelum-pemulihan|sebelum-hapus)-[0-9-]+\\.json$");
    static final String BACKUPS_DIR = "backups";

    private final Path dataDir;

    public SafeDataFolder(Path dataDir) throws StorageException {
        Path absolute = dataDir.toAbsolutePath().normalize();
        if (absolute.getParent() == null || absolute.getNameCount() < 2) {
            throw new StorageException("Folder data tidak aman untuk dihapus isinya: " + absolute, null);
        }
        Path home = Path.of(System.getProperty("user.home", "")).toAbsolutePath().normalize();
        if (absolute.equals(home)) throw new StorageException("Folder data tidak boleh sama dengan folder home", null);
        if (Files.isSymbolicLink(absolute)) throw new StorageException("Folder data adalah symbolic link, penghapusan dibatalkan", null);
        this.dataDir = absolute;
    }

    public Path dataDir() {
        return dataDir;
    }

    /** True for a name the app created in the data folder itself (a bare file name). */
    public static boolean isOwnedDataName(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) return false;
        for (String base : DATA_FILES) {
            if (name.equals(base) || name.equals(base + ".bak") || name.equals(base + ".tmp")) return true;
        }
        return name.equals(SETTINGS_FILE) || CORRUPT_COPY.matcher(name).matches();
    }

    /** True for a bare file name that is an automatic or safety backup. */
    public static boolean isOwnedBackupName(String name) {
        return name != null && !name.contains("/") && !name.contains("\\") && APP_BACKUP.matcher(name).matches();
    }

    /**
     * The files that would be removed by deleting one set of data: names relative to the data folder (backups are
     * "backups/&lt;name&gt;"). Refuses the whole listing if any of them is unsafe.
     */
    public List<String> existingDataFiles() throws StorageException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = list(dataDir)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                if (isOwnedDataName(name)) names.add(checked(p, name));
            }
        } catch (IOException e) {
            throw new StorageException("Isi folder data tidak bisa dibaca: " + e.getMessage(), e);
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** The automatic and safety backups the app created. Other files in that folder are left out on purpose. */
    public List<String> existingBackupFiles() throws StorageException {
        Path backups = dataDir.resolve(BACKUPS_DIR);
        if (!Files.exists(backups, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(backups) || !Files.isDirectory(backups, LinkOption.NOFOLLOW_LINKS)) {
            throw new StorageException("Folder 'backups' adalah symbolic link atau bukan folder, penghapusan dibatalkan", null);
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = list(backups)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                if (isOwnedBackupName(name)) names.add(checked(p, BACKUPS_DIR + "/" + name));
            }
        } catch (IOException e) {
            throw new StorageException("Isi folder cadangan tidak bisa dibaca: " + e.getMessage(), e);
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** Throws unless the name is something the app may delete; returns its path inside the data folder. */
    public Path resolveOwned(String relativeName) throws StorageException {
        boolean inBackups = relativeName.startsWith(BACKUPS_DIR + "/");
        String bare = inBackups ? relativeName.substring(BACKUPS_DIR.length() + 1) : relativeName;
        boolean ok = inBackups ? isOwnedBackupName(bare) : isOwnedDataName(bare);
        if (!ok) throw new StorageException("Bukan file milik aplikasi, tidak akan dihapus: " + relativeName, null);
        Path path = dataDir.resolve(relativeName).normalize();
        if (!path.startsWith(dataDir)) throw new StorageException("Path di luar folder data ditolak: " + relativeName, null);
        checked(path, relativeName);
        return path;
    }

    // ---- internals ----

    private static Stream<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return Stream.empty();
        return Files.list(dir).filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(p));
    }

    /** Refuses a symbolic link; returns the name when the entry is a plain file. */
    private String checked(Path path, String name) throws StorageException {
        if (Files.isSymbolicLink(path)) {
            throw new StorageException("'" + name + "' adalah symbolic link dan tidak akan diikuti. Penghapusan dibatalkan; tidak ada data yang berubah.", null);
        }
        return name;
    }
}
