package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.StorageException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Replaces several files as one unit: all of them change, or none of them do.
 * <ol>
 *   <li>Every new content is first written to a temporary file next to its target.</li>
 *   <li>Then, file by file, the current file is moved aside (".restore-rollback") and the temporary file moved into place.</li>
 *   <li>If any step fails, everything already moved is put back and the temporary files are removed.</li>
 *   <li>On success the caller decides: {@link Swap#commit()} deletes the rollback copies, {@link Swap#rollback()} restores
 *       the originals (used when something after the swap, such as reloading the app, fails).</li>
 * </ol>
 * Moves go through a {@link Mover}, which retries (Windows refuses to move a file another program has open).
 */
public class FileSwapper {
    static final String TEMP_SUFFIX = ".restore-tmp";
    static final String ROLLBACK_SUFFIX = ".restore-rollback";

    /** Moves one file, replacing the target. A seam, so tests can make a move fail at a chosen step. */
    @FunctionalInterface
    public interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    /** Atomic move when the file system supports it; retries a few times for files that are briefly locked. */
    public static Mover retryingMover(int attempts, long delayMillis) {
        return (from, to) -> {
            IOException last = null;
            for (int i = 0; i < attempts; i++) {
                try {
                    try {
                        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                } catch (IOException e) {
                    last = e;
                    if (i + 1 < attempts) {
                        try {
                            Thread.sleep(delayMillis);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            throw last;
        };
    }

    private final Mover mover;

    public FileSwapper() {
        this(retryingMover(5, 200));
    }

    public FileSwapper(Mover mover) {
        this.mover = mover;
    }

    /** One file that was replaced: where it lives and where its previous content was moved (null if it did not exist). */
    private record Replaced(Path target, Path rollback) {}

    /** The result of a successful swap: keep it ({@link #commit}) or undo it ({@link #rollback}). */
    public final class Swap {
        private final List<Replaced> replaced;
        private boolean finished;

        private Swap(List<Replaced> replaced) {
            this.replaced = replaced;
        }

        /** Makes the swap permanent: the previous copies are deleted. */
        public void commit() {
            if (finished) return;
            finished = true;
            for (Replaced r : replaced) {
                if (r.rollback() != null) {
                    try {
                        Files.deleteIfExists(r.rollback());
                    } catch (IOException ignored) {
                        // a leftover rollback file is harmless; it is overwritten by the next restore
                    }
                }
            }
        }

        /** Puts every original back. */
        public void rollback() throws StorageException {
            if (finished) return;
            finished = true;
            undo(replaced);
        }
    }

    /**
     * Replaces (or creates) the given files inside {@code dir}.
     *
     * @param contents file name to new text content (written as UTF-8)
     * @throws StorageException if anything fails; in that case every file is exactly as it was before the call
     */
    public Swap swap(Path dir, Map<String, String> contents) throws StorageException {
        List<Path> temps = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, String> e : contents.entrySet()) {
                Path temp = dir.resolve(e.getKey() + TEMP_SUFFIX);
                temps.add(temp);
                Files.write(temp, e.getValue().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            deleteAll(temps);
            throw new StorageException("Gagal menyiapkan file pemulihan: " + e.getMessage(), e);
        }

        List<Replaced> done = new ArrayList<>();
        try {
            for (String name : contents.keySet()) {
                Path target = dir.resolve(name);
                Path temp = dir.resolve(name + TEMP_SUFFIX);
                Path rollback = null;
                if (Files.exists(target)) {
                    rollback = dir.resolve(name + ROLLBACK_SUFFIX);
                    mover.move(target, rollback);                 // 1. the current file steps aside
                }
                done.add(new Replaced(target, rollback));         // recorded before step 2 so a failure there is undone too
                mover.move(temp, target);                          // 2. the new file takes its place
            }
        } catch (IOException e) {
            try {
                undo(done);
            } finally {
                deleteAll(temps);
            }
            throw new StorageException(lockedMessage(e), e);
        }
        return new Swap(done);
    }

    private void undo(List<Replaced> done) throws StorageException {
        IOException failure = null;
        for (int i = done.size() - 1; i >= 0; i--) {
            Replaced r = done.get(i);
            try {
                if (r.rollback() != null && Files.exists(r.rollback())) {
                    mover.move(r.rollback(), r.target());
                } else if (r.rollback() == null) {
                    Files.deleteIfExists(r.target());            // the file did not exist before: remove the new one
                }
            } catch (IOException e) {
                failure = e;
            }
        }
        if (failure != null) {
            throw new StorageException("Data sebelumnya tidak bisa dikembalikan otomatis. Salinan lama ada di file berakhiran "
                    + ROLLBACK_SUFFIX + " di folder data: " + failure.getMessage(), failure);
        }
    }

    private static void deleteAll(List<Path> paths) {
        for (Path p : paths) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static String lockedMessage(IOException e) {
        return "Gagal mengganti file data (mungkin sedang dipakai program lain). Tutup program yang membukanya lalu coba lagi. "
                + "Tidak ada data yang berubah. Rincian: " + e.getMessage();
    }
}
