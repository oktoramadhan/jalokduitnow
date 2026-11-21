package com.okto.jalokduitnow.thread;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
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
    /**
     * One lock for every auto-save in the app. Normal saves take it only while writing. A restore holds it while it swaps
     * the data files, and throws away every save still waiting, so no older snapshot can be written over restored files.
     */
    private static final ReentrantLock GATE = new ReentrantLock();
    private static final Set<AutoSaveService<?>> INSTANCES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

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
        INSTANCES.add(this);
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

    private void writePending() {
        GATE.lock();
        try {
            scheduled.set(false);
            List<T> snapshot = pending.getAndSet(null);
            if (snapshot == null) return;
            try {
                repository.saveAll(snapshot);
            } catch (StorageException e) {
                errorHandler.accept(e);
            }
        } finally {
            GATE.unlock();
        }
    }

    /**
     * Runs {@code action} while no auto-save can write, after discarding every save that was waiting. Used when the data
     * files are replaced from outside (a restore): the waiting snapshots describe the OLD data and must never be written.
     * The action must be short and must not wait for the FX thread (the FX thread may be waiting for this lock).
     */
    public static <R> R exclusively(Callable<R> action) throws Exception {
        GATE.lock();
        try {
            synchronized (INSTANCES) {
                for (AutoSaveService<?> service : INSTANCES) service.pending.set(null);
            }
            return action.call();
        } finally {
            GATE.unlock();
        }
    }
}
