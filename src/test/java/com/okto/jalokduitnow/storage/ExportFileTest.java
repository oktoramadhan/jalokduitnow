package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ExportFileTest {
    @TempDir Path dir;

    @Test
    void writesUtf8TextAndLeavesNoTemporaryFile() throws Exception {
        Path file = dir.resolve("out.csv");
        Path written = ExportFile.write(file, "kategori,jumlah\ncafé,1\n", false);
        assertEquals(file.toAbsolutePath(), written);
        assertEquals("kategori,jumlah\ncafé,1\n", Files.readString(file, StandardCharsets.UTF_8));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(1, files.count(), "only the export file remains");
        }
    }

    @Test
    void theByteOrderMarkIsWrittenOnlyWhenAsked() throws Exception {
        byte[] with = Files.readAllBytes(ExportFile.write(dir.resolve("a.csv"), "x", true));
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'x'}, with);
        byte[] without = Files.readAllBytes(ExportFile.write(dir.resolve("b.csv"), "x", false));
        assertArrayEquals(new byte[]{'x'}, without);
    }

    @Test
    void anExistingFileIsReplacedAndMissingFoldersAreCreated() throws Exception {
        Path file = dir.resolve("sub/folder/out.json");
        ExportFile.write(file, "lama", false);
        ExportFile.write(file, "baru", false);
        assertEquals("baru", Files.readString(file));
    }

    @Test
    void aFailureThrowsStorageExceptionAndKeepsTheOldFile() throws Exception {
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "i am a file, not a folder");
        assertThrows(StorageException.class, () -> ExportFile.write(blocker.resolve("out.csv"), "x", false));
        assertEquals("i am a file, not a folder", Files.readString(blocker));

        Path keep = dir.resolve("keep.csv");
        Files.writeString(keep, "asli");
        Path asDirectory = dir.resolve("asdir");
        Files.createDirectory(asDirectory);
        assertThrows(StorageException.class, () -> ExportFile.write(asDirectory, "x", false));   // target is a folder
        assertEquals("asli", Files.readString(keep));
        assertFalse(Files.exists(dir.resolve("asdir.tmp")), "temporary file is cleaned up after a failure");
    }
}
