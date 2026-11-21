package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.ReminderLogDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The record of what has already fired: one key per reminder and period ("daily-log@2026-11-21"). It is saved
 * (reminder-log.json), so a reminder that fired is not fired again after the app restarts. Old records are
 * dropped after about two months: by then their day or month is long over, so the key can never match again.
 */
public class ReminderLog {
    static final Duration KEEP = Duration.ofDays(62);

    private final Repository<ReminderLogDto> repository;
    private final AutoSaveService<ReminderLogDto> autoSave;
    private final Map<String, Instant> entries = new LinkedHashMap<>();

    public ReminderLog(Repository<ReminderLogDto> repository) {
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
    }

    public void setErrorHandler(Consumer<StorageException> handler) { autoSave.setErrorHandler(handler); }

    public void flush() { autoSave.flush(); }

    public void load() throws StorageException {
        entries.clear();
        for (ReminderLogDto dto : repository.findAll()) entries.put(dto.key(), dto.firedAt());
    }

    public boolean hasFired(String key) {
        return entries.containsKey(key);
    }

    /** Remembers a firing and forgets records that are too old to matter. */
    public void record(String key, Instant now) {
        entries.put(key, now);
        entries.values().removeIf(at -> at.isBefore(now.minus(KEEP)));
        autoSave.save(entries.entrySet().stream().map(e -> new ReminderLogDto(e.getKey(), e.getValue())).collect(Collectors.toList()));
    }

    public int size() {
        return entries.size();
    }
}
