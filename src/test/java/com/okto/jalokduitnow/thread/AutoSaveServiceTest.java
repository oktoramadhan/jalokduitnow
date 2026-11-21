package com.okto.jalokduitnow.thread;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.Repository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AutoSaveServiceTest {
    /** Records every save and remembers which thread did it. */
    static class FakeRepo implements Repository<String> {
        final List<List<String>> saves = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<String> thread = new AtomicReference<>();
        final CountDownLatch first = new CountDownLatch(1);
        boolean fail;

        @Override public List<String> findAll() { return List.of(); }

        @Override public void saveAll(List<String> items) throws StorageException {
            thread.set(Thread.currentThread().getName());
            if (fail) throw new StorageException("boom", null);
            saves.add(items);
            first.countDown();
        }
    }

    @Test
    void writesOnABackgroundThreadAfterTheDelay() throws Exception {
        FakeRepo repo = new FakeRepo();
        AutoSaveService<String> auto = new AutoSaveService<>(repo, 50);
        auto.save(List.of("a"));
        assertTrue(repo.first.await(2, TimeUnit.SECONDS));
        assertEquals("auto-save", repo.thread.get());
        assertNotEquals(Thread.currentThread().getName(), repo.thread.get());
        auto.shutdown();
    }

    @Test
    void quickChangesAreCoalescedIntoTheNewestSnapshot() {
        FakeRepo repo = new FakeRepo();
        AutoSaveService<String> auto = new AutoSaveService<>(repo, 10_000); // long delay: only flush writes
        auto.save(List.of("1"));
        auto.save(List.of("1", "2"));
        auto.save(List.of("1", "2", "3"));
        auto.flush();
        assertEquals(1, repo.saves.size());
        assertEquals(List.of("1", "2", "3"), repo.saves.get(0));
        auto.flush(); // nothing pending: no extra write
        assertEquals(1, repo.saves.size());
        auto.shutdown();
    }

    @Test
    void snapshotIsCopiedSoLaterChangesDoNotLeakIn() {
        FakeRepo repo = new FakeRepo();
        AutoSaveService<String> auto = new AutoSaveService<>(repo, 10_000);
        List<String> mutable = new ArrayList<>(List.of("a"));
        auto.save(mutable);
        mutable.add("b");
        auto.flush();
        assertEquals(List.of("a"), repo.saves.get(0));
        auto.shutdown();
    }

    @Test
    void errorsGoToTheHandler() {
        FakeRepo repo = new FakeRepo();
        repo.fail = true;
        AutoSaveService<String> auto = new AutoSaveService<>(repo, 10_000);
        AtomicReference<String> seen = new AtomicReference<>();
        auto.setErrorHandler(e -> seen.set(e.getMessage()));
        auto.save(List.of("a"));
        auto.flush();
        assertEquals("boom", seen.get());
        auto.shutdown();
    }
}
