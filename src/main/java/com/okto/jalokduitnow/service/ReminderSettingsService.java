package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidSettingException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.reminder.ReminderKind;
import com.okto.jalokduitnow.reminder.ReminderSettings;
import com.okto.jalokduitnow.storage.ReminderSettingsDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Keeps the one live ReminderSettings object and saves it (reminders.json) after every change.
 * Readers get a copy from {@link #current()}; all changes go through the methods here, which validate first and
 * only then save and notify, so an invalid value changes nothing.
 */
public class ReminderSettingsService {
    private final Repository<ReminderSettingsDto> repository;
    private final AutoSaveService<ReminderSettingsDto> autoSave;
    private ReminderSettings settings = new ReminderSettings();
    private final List<Runnable> listeners = new ArrayList<>();

    public ReminderSettingsService(Repository<ReminderSettingsDto> repository) {
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
    }

    public void addListener(Runnable listener) { listeners.add(listener); }

    public void setErrorHandler(Consumer<StorageException> handler) { autoSave.setErrorHandler(handler); }

    public void flush() { autoSave.flush(); }

    public void load() throws StorageException {
        List<ReminderSettingsDto> saved = repository.findAll();
        if (saved.isEmpty()) return;
        try {
            settings = saved.get(0).toSettings();
        } catch (InvalidSettingException e) {
            throw new StorageException("File pengaturan pengingat rusak: " + e.getMessage(), e);
        }
    }

    /** A snapshot of the settings (changing it does not change the live settings). */
    public ReminderSettings current() {
        return settings.copy();
    }

    // ---- changes: validate, then save ----

    @FunctionalInterface
    private interface Change { void apply(ReminderSettings s) throws InvalidSettingException; }

    private void change(Change change) throws InvalidSettingException {
        ReminderSettings working = settings.copy();   // validate on a copy: a rejected change leaves the live settings untouched
        change.apply(working);
        settings = working;
        autoSave.save(List.of(ReminderSettingsDto.from(settings)));
        listeners.forEach(Runnable::run);
    }

    public void setEnabled(ReminderKind kind, boolean enabled) throws InvalidSettingException {
        change(s -> s.setEnabled(kind, enabled));
    }

    public void setDailyLogTime(LocalTime time) throws InvalidSettingException {
        change(s -> s.setDailyLogTime(time));
    }

    public void setStreakRiskTime(LocalTime time) throws InvalidSettingException {
        change(s -> s.setStreakRiskTime(time));
    }

    public void setDonorReport(int dayOfMonth, LocalTime time) throws InvalidSettingException {
        change(s -> s.setDonorReport(dayOfMonth, time));
    }

    public void setBudgetWarnPercent(int percent) throws InvalidSettingException {
        change(s -> s.setBudgetWarnPercent(percent));
    }

    public void setQuietHours(boolean enabled, LocalTime start, LocalTime end) throws InvalidSettingException {
        change(s -> s.setQuietHours(enabled, start, end));
    }

    public void setTrayEnabled(boolean enabled) throws InvalidSettingException {
        change(s -> s.setTrayEnabled(enabled));
    }
}
