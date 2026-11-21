package com.okto.jalokduitnow.thread;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.Repository;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Saves a repository on a background thread so the UI never waits for disk.
 * Many quick changes are coalesced: only the newest snapshot is written, after a short delay.
 * Callers must pass an immutable snapshot taken on their own thread (no shared mutable state).
 * Errors are delivered to the error handler; the UI side must hop to the FX thread itself.
 */
public class AutoSaveService<T> {
    private final Repository<T> repository;
    private final long delayMillis;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auto-save");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<List<T>> pending = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private volatile Consumer<StorageException> errorHandler = e -> System.err.println(e.getMessage());

    public AutoSaveService(Repository<T> repository, long delayMillis) {
        this.repository = repository;
        this.delayMillis = delayMillis;
    }

    public void setErrorHandler(Consumer<StorageException> handler) {
        this.errorHandler = handler;
    }

    /** Remembers the newest snapshot and schedules a write if none is already waiting. */
    public void save(List<T> snapshot) {
        pending.set(List.copyOf(snapshot));
        if (scheduled.compareAndSet(false, true)) {
            executor.schedule(this::writePending, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    /** Writes any waiting snapshot now, on the calling thread. Call before the app exits. */
    public void flush() {
        writePending();
    }

    public void shutdown() {
        flush();
        executor.shutdown();
    }

    private synchronized void writePending() {
        scheduled.set(false);
        List<T> snapshot = pending.getAndSet(null);
        if (snapshot == null) return;
        try {
            repository.saveAll(snapshot);
        } catch (StorageException e) {
            errorHandler.accept(e);
        }
    }
}
