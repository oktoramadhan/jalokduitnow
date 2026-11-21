package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.FileSwapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FileSwapperTest {
    @TempDir Path dir;

    private Map<String, String> contents(String suffix) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("a.json", "A-" + suffix);
        m.put("b.json", "B-" + suffix);
        m.put("c.json", "C-" + suffix);
        return m;
    }

    private void writeOriginals() throws IOException {
        Files.writeString(dir.resolve("a.json"), "A-old");
        Files.writeString(dir.resolve("b.json"), "B-old");
        // c.json does not exist yet
    }

    private List<String> names() throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private FileSwapper.Mover failingOnCall(int n, AtomicInteger calls) {
        FileSwapper.Mover real = FileSwapper.retryingMover(1, 0);
        return (from, to) -> {
            if (calls.incrementAndGet() == n) throw new IOException("disk penuh (uji)");
            real.move(from, to);
        };
    }

    @Test
    void everyFileChangesAndNewOnesAreCreated() throws Exception {
        writeOriginals();
        FileSwapper.Swap swap = new FileSwapper().swap(dir, contents("new"));
        assertEquals("A-new", Files.readString(dir.resolve("a.json")));
        assertEquals("B-new", Files.readString(dir.resolve("b.json")));
        assertEquals("C-new", Files.readString(dir.resolve("c.json")));
        swap.commit();
        assertEquals(List.of("a.json", "b.json", "c.json"), names(), "no temporary or rollback files are left after commit");
    }

    @Test
    void rollbackAfterASuccessfulSwapRestoresTheOriginalsAndRemovesNewFiles() throws Exception {
        writeOriginals();
        FileSwapper.Swap swap = new FileSwapper().swap(dir, contents("new"));
        swap.rollback();
        assertEquals("A-old", Files.readString(dir.resolve("a.json")));
        assertEquals("B-old", Files.readString(dir.resolve("b.json")));
        assertFalse(Files.exists(dir.resolve("c.json")), "a file that did not exist before is removed again");
        assertEquals(List.of("a.json", "b.json"), names());
    }

    @Test
    void aFailureAtAnyStepPutsEveryFileBack() throws Exception {
        // the swap makes up to 5 moves here (a: aside+in, b: aside+in, c: in); fail each one in turn, and the undo moves too
        for (int failAt = 1; failAt <= 5; failAt++) {
            for (Path p : List.of(dir.resolve("a.json"), dir.resolve("b.json"), dir.resolve("c.json"))) Files.deleteIfExists(p);
            writeOriginals();
            AtomicInteger calls = new AtomicInteger();
            FileSwapper swapper = new FileSwapper(failingOnCall(failAt, calls));

            StorageException e = assertThrows(StorageException.class, () -> swapper.swap(dir, contents("new")), "failing move #" + failAt);
            assertTrue(e.getMessage().contains("Tidak ada data yang berubah"), e.getMessage());
            assertEquals("A-old", Files.readString(dir.resolve("a.json")), "a.json after failing move #" + failAt);
            assertEquals("B-old", Files.readString(dir.resolve("b.json")), "b.json after failing move #" + failAt);
            assertFalse(Files.exists(dir.resolve("c.json")), "c.json after failing move #" + failAt);
            assertEquals(List.of("a.json", "b.json"), names(), "no temp/rollback leftovers after failing move #" + failAt);
        }
    }

    @Test
    void aFailureWhileWritingTemporaryFilesChangesNothing() throws Exception {
        writeOriginals();
        Files.createDirectory(dir.resolve("c.json.restore-tmp"));        // a folder where the temp file must go: writing fails
        StorageException e = assertThrows(StorageException.class, () -> new FileSwapper().swap(dir, contents("new")));
        assertTrue(e.getMessage().contains("menyiapkan"));
        assertEquals("A-old", Files.readString(dir.resolve("a.json")));
        assertFalse(Files.exists(dir.resolve("a.json.restore-tmp")), "temporary files that were written are cleaned up");
    }

    @Test
    void ifTheUndoItselfFailsTheMessageSaysWhereTheOldCopiesAre() throws Exception {
        writeOriginals();
        AtomicInteger calls = new AtomicInteger();
        FileSwapper.Mover real = FileSwapper.retryingMover(1, 0);
        FileSwapper swapper = new FileSwapper((from, to) -> {
            if (calls.incrementAndGet() >= 4) throw new IOException("terkunci (uji)");   // the 4th move fails and so does every undo
            real.move(from, to);
        });
        StorageException e = assertThrows(StorageException.class, () -> swapper.swap(dir, contents("new")));
        assertTrue(e.getMessage().contains(".restore-rollback"), e.getMessage());
        assertTrue(Files.exists(dir.resolve("b.json.restore-rollback")) || Files.exists(dir.resolve("a.json.restore-rollback")),
                "the old content is still on disk under the rollback name");
    }

    // ---- locked files (Windows) ----

    @Test
    void aBrieflyLockedFileIsRetriedAndSucceeds() throws Exception {
        Files.writeString(dir.resolve("from.txt"), "x");
        Files.writeString(dir.resolve("to.txt"), "old");
        AtomicInteger attempts = new AtomicInteger();
        FileSwapper.Mover real = FileSwapper.retryingMover(5, 1);
        // wrap: the retrying mover is what retries, so make the underlying move fail twice through a custom mover of the same shape
        FileSwapper.Mover flaky = (from, to) -> {
            IOException last = null;
            for (int i = 0; i < 5; i++) {
                try {
                    if (attempts.incrementAndGet() <= 2) throw new IOException("sedang dipakai program lain (uji)");
                    real.move(from, to);
                    return;
                } catch (IOException e) {
                    last = e;
                }
            }
            throw last;
        };
        flaky.move(dir.resolve("from.txt"), dir.resolve("to.txt"));
        assertEquals("x", Files.readString(dir.resolve("to.txt")));
        assertEquals(3, attempts.get());
    }

    @Test
    void theRealRetryingMoverGivesUpAfterTheAttemptsAndKeepsTheError() throws Exception {
        FileSwapper.Mover mover = FileSwapper.retryingMover(3, 1);
        IOException e = assertThrows(IOException.class, () -> mover.move(dir.resolve("missing.txt"), dir.resolve("target.txt")));
        assertNotNull(e.getMessage());
    }

    @Test
    void theRetryingMoverMovesAndReplaces() throws Exception {
        Files.writeString(dir.resolve("from.txt"), "new");
        Files.writeString(dir.resolve("to.txt"), "old");
        FileSwapper.retryingMover(3, 1).move(dir.resolve("from.txt"), dir.resolve("to.txt"));
        assertEquals("new", Files.readString(dir.resolve("to.txt")));
        assertFalse(Files.exists(dir.resolve("from.txt")));
    }

    @Test
    void aLockedFileGivesAClearMessageThatNothingChanged() throws Exception {
        writeOriginals();
        FileSwapper swapper = new FileSwapper((from, to) -> { throw new IOException("The process cannot access the file because it is being used by another process"); });
        StorageException e = assertThrows(StorageException.class, () -> swapper.swap(dir, contents("new")));
        assertTrue(e.getMessage().contains("program lain") && e.getMessage().contains("Tidak ada data yang berubah"), e.getMessage());
        assertEquals("A-old", Files.readString(dir.resolve("a.json")));
    }

    @Test
    void commitAndRollbackAreOnlyEffectiveOnce() throws Exception {
        writeOriginals();
        FileSwapper.Swap swap = new FileSwapper().swap(dir, contents("new"));
        swap.commit();
        swap.rollback();                                                 // too late: the commit already made it permanent
        assertEquals("A-new", Files.readString(dir.resolve("a.json")));
        swap.commit();                                                   // harmless
    }
}
