package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.DonorSettingsDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.util.*;
import java.util.function.Consumer;

/** Which income categories count as "money from a donor" in the donor report. Saved to donor-settings.json. */
public class DonorSettings {
    private final Repository<DonorSettingsDto> repository;
    private final AutoSaveService<DonorSettingsDto> autoSave;
    private final Set<UUID> donorCategories = new LinkedHashSet<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public DonorSettings(Repository<DonorSettingsDto> repository) {
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
    }

    public void addListener(Runnable listener) { listeners.add(listener); }

    public void setErrorHandler(Consumer<StorageException> handler) { autoSave.setErrorHandler(handler); }

    public void flush() { autoSave.flush(); }

    public void load() throws StorageException {
        donorCategories.clear();
        List<DonorSettingsDto> saved = repository.findAll();
        if (!saved.isEmpty() && saved.get(0).donorCategoryIds() != null) donorCategories.addAll(saved.get(0).donorCategoryIds());
    }

    public void reload() throws StorageException {
        load();
        listeners.forEach(Runnable::run);
    }

    public Set<UUID> donorCategoryIds() {
        return Set.copyOf(donorCategories);
    }

    public boolean isDonorCategory(UUID categoryId) {
        return donorCategories.contains(categoryId);
    }

    public void setDonorCategory(UUID categoryId, boolean donor) {
        boolean changed = donor ? donorCategories.add(categoryId) : donorCategories.remove(categoryId);
        if (!changed) return;
        autoSave.save(List.of(new DonorSettingsDto(List.copyOf(donorCategories))));
        listeners.forEach(Runnable::run);
    }
}
