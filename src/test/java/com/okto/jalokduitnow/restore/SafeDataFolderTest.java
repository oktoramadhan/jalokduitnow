package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.SafeDataFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The only gate that decides which files the app may delete: nothing outside the data folder, only files the app created. */
class SafeDataFolderTest {
    @TempDir Path root;

    private Path data() throws IOException {
        return Files.createDirectories(root.resolve("app-data"));
    }

    private static void touch(Path p) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, "x");
    }

    /** Creates a symbolic link or skips the test where the system does not allow it (Windows without the privilege). */
    private static void link(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "symbolic links are not available here: " + e);
        }
    }

    // ---- which names the app owns ----

    @Test
    void theAppsOwnDataFilesAndTheirCopiesAreOwned() {
        for (String name : List.of("transactions.json", "schema.json", "categories.json", "game.json", "recipients.json",
                "donor-settings.json", "reminders.json", "reminder-log.json", "notifications.json", "settings.properties",
                "transactions.json.bak", "game.json.bak", "schema.json.tmp",
                "transactions.json.corrupt-20261121153012.bak", "categories.json.corrupt-20261121153012-2.bak")) {
            assertTrue(SafeDataFolder.isOwnedDataName(name), name);
        }
    }

    @Test
    void anythingElseIsNotOwnedAndWillNeverBeDeleted() {
        for (String name : List.of("", " ", "notes.txt", "my-backup.json", "transactions.json.old", "transactions.JSON", "xtransactions.json",
                "photo.png", "../transactions.json", "..", ".", "sub/transactions.json", "sub\\transactions.json",
                "/etc/passwd", "C:\\Windows\\win.ini", "transactions.json/", "backups", "transactions.json.corrupt-abc.bak",
                "transactions.json.bak.bak")) {
            assertFalse(SafeDataFolder.isOwnedDataName(name), "'" + name + "' must not be owned");
        }
        assertFalse(SafeDataFolder.isOwnedDataName(null));
    }

    @Test
    void onlyAutomaticAndSafetyBackupsAreOwnedBackups() {
        for (String name : List.of("otomatis-2026-11-21.json", "sebelum-pemulihan-20261121-160509.json",
                "sebelum-pemulihan-20261121-160509-2.json", "sebelum-hapus-20261121-160509.json")) {
            assertTrue(SafeDataFolder.isOwnedBackupName(name), name);
        }
        for (String name : List.of("cadangan-saya.json", "otomatis-.txt", "otomatis-2026-11-21.json.bak", "../otomatis-2026-11-21.json",
                "sub/otomatis-2026-11-21.json", "otomatisX-2026.json", "backup.json", "")) {
            assertFalse(SafeDataFolder.isOwnedBackupName(name), "'" + name + "' must not be owned");
        }
        assertFalse(SafeDataFolder.isOwnedBackupName(null));
    }

    // ---- resolving and listing ----

    @Test
    void resolveRefusesEverythingThatIsNotOwnedOrEscapesTheFolder() throws Exception {
        SafeDataFolder folder = new SafeDataFolder(data());
        assertEquals(data().toAbsolutePath().normalize().resolve("transactions.json"), folder.resolveOwned("transactions.json"));
        assertEquals(data().toAbsolutePath().normalize().resolve("backups/otomatis-2026-11-21.json"),
                folder.resolveOwned("backups/otomatis-2026-11-21.json"));
        for (String bad : List.of("../outside.json", "../../etc/passwd", "/etc/passwd", "backups/../transactions.json.x", "notes.txt",
                "backups/notes.txt", "backups/../../x.json", "backups/sub/otomatis-2026-11-21.json", "backups", "")) {
            assertThrows(StorageException.class, () -> folder.resolveOwned(bad), bad);
        }
    }

    @Test
    void listingFindsOnlyFilesTheAppCreated() throws Exception {
        Path d = data();
        for (String n : List.of("transactions.json", "transactions.json.bak", "schema.json", "settings.properties", "game.json.corrupt-20261121153012.bak")) touch(d.resolve(n));
        // things that are NOT the app's: must never appear in a list that can be deleted
        for (String n : List.of("notes.txt", "mijn-cadangan.json", "photo.png", "transactions.json.old")) touch(d.resolve(n));
        touch(d.resolve("subfolder/transactions.json"));
        touch(d.resolve("backups/otomatis-2026-11-21.json"));
        touch(d.resolve("backups/sebelum-hapus-20261121-160509.json"));
        touch(d.resolve("backups/cadangan-yang-kusimpan-sendiri.json"));
        touch(d.resolve("backups/catatan.txt"));

        SafeDataFolder folder = new SafeDataFolder(d);
        assertEquals(List.of("game.json.corrupt-20261121153012.bak", "schema.json", "settings.properties", "transactions.json", "transactions.json.bak"),
                folder.existingDataFiles());
        assertEquals(List.of("backups/otomatis-2026-11-21.json", "backups/sebelum-hapus-20261121-160509.json"), folder.existingBackupFiles());
    }

    @Test
    void anEmptyOrMissingFolderListsNothing() throws Exception {
        SafeDataFolder folder = new SafeDataFolder(root.resolve("does-not-exist"));
        assertTrue(folder.existingDataFiles().isEmpty());
        assertTrue(folder.existingBackupFiles().isEmpty());
    }

    // ---- unsafe data folders ----

    @Test
    void aRootOrTheHomeFolderIsNeverAcceptedAsADataFolder() {
        assertThrows(StorageException.class, () -> new SafeDataFolder(Path.of("/")));
        assertThrows(StorageException.class, () -> new SafeDataFolder(root.getRoot()));
        assertThrows(StorageException.class, () -> new SafeDataFolder(Path.of(System.getProperty("user.home"))));
        assertThrows(StorageException.class, () -> new SafeDataFolder(Path.of(System.getProperty("user.home")).resolve("x").resolve("..")),
                "a path that normalizes to home is home");
    }

    @Test
    void aDataFolderThatIsASymbolicLinkIsRefused() throws Exception {
        Path target = Files.createDirectories(root.resolve("elsewhere"));
        Path link = root.resolve("link-to-elsewhere");
        link(link, target);
        StorageException e = assertThrows(StorageException.class, () -> new SafeDataFolder(link));
        assertTrue(e.getMessage().contains("symbolic link"));
    }

    // ---- symbolic links inside the folder ----

    @Test
    void aListedFileThatIsASymbolicLinkRefusesTheWholeListing() throws Exception {
        Path d = data();
        Path outside = root.resolve("precious-outside-file.json");
        Files.writeString(outside, "do not touch");
        touch(d.resolve("schema.json"));
        link(d.resolve("transactions.json"), outside);

        SafeDataFolder folder = new SafeDataFolder(d);
        StorageException e = assertThrows(StorageException.class, folder::existingDataFiles);
        assertTrue(e.getMessage().contains("symbolic link") && e.getMessage().contains("tidak ada data yang berubah"), e.getMessage());
        assertThrows(StorageException.class, () -> folder.resolveOwned("transactions.json"));
        assertEquals("do not touch", Files.readString(outside));
    }

    @Test
    void aBackupsFolderThatIsASymbolicLinkIsRefused() throws Exception {
        Path d = data();
        Path outsideDir = Files.createDirectories(root.resolve("outside-dir"));
        touch(outsideDir.resolve("otomatis-2026-11-21.json"));
        link(d.resolve("backups"), outsideDir);
        SafeDataFolder folder = new SafeDataFolder(d);
        assertThrows(StorageException.class, folder::existingBackupFiles);
        assertTrue(Files.exists(outsideDir.resolve("otomatis-2026-11-21.json")));
    }

    @Test
    void aBackupFileThatIsASymbolicLinkIsRefused() throws Exception {
        Path d = data();
        Path outside = root.resolve("outside.json");
        Files.writeString(outside, "do not touch");
        Files.createDirectories(d.resolve("backups"));
        link(d.resolve("backups/otomatis-2026-11-21.json"), outside);
        assertThrows(StorageException.class, () -> new SafeDataFolder(d).existingBackupFiles());
        assertEquals("do not touch", Files.readString(outside));
    }
}
