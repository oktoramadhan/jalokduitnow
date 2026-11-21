package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidRecipientException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.Recipient;
import com.okto.jalokduitnow.storage.RecipientDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.util.*;
import java.util.function.Consumer;

/**
 * Owns the recipients (donors) and keeps "exactly one default, or none when the list is empty" true:
 * the first recipient becomes the default, setting a new default clears the old one, and deleting the default
 * hands the role to the next recipient. Saved on a background thread to recipients.json.
 */
public class RecipientService {
    private final Repository<RecipientDto> repository;
    private final AutoSaveService<RecipientDto> autoSave;
    private final List<Recipient> recipients = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public RecipientService(Repository<RecipientDto> repository) {
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
    }

    public void addListener(Runnable listener) { listeners.add(listener); }

    public void setErrorHandler(Consumer<StorageException> handler) { autoSave.setErrorHandler(handler); }

    public void flush() { autoSave.flush(); }

    public void load() throws StorageException {
        recipients.clear();
        try {
            for (RecipientDto dto : repository.findAll()) recipients.add(dto.toRecipient());
        } catch (InvalidRecipientException e) {
            throw new StorageException("File penerima rusak: " + e.getMessage(), e);
        }
        ensureOneDefault();
    }

    public List<Recipient> getAll() {
        return List.copyOf(recipients);
    }

    public Optional<Recipient> find(UUID id) {
        return recipients.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    public Optional<Recipient> getDefault() {
        return recipients.stream().filter(Recipient::isDefault).findFirst();
    }

    public Recipient add(String name, String rawPhone, String note, boolean makeDefault) throws InvalidRecipientException {
        Recipient r = Recipient.create(name, rawPhone, note);
        recipients.add(r);
        if (makeDefault || recipients.size() == 1) applyDefault(r);
        save();
        return r;
    }

    public void update(UUID id, String name, String rawPhone, String note) throws InvalidRecipientException {
        require(id).update(name, rawPhone, note);
        save();
    }

    public void delete(UUID id) throws InvalidRecipientException {
        Recipient r = require(id);
        recipients.remove(r);
        ensureOneDefault();
        save();
    }

    public void setDefault(UUID id) throws InvalidRecipientException {
        applyDefault(require(id));
        save();
    }

    // ---- internals ----

    private Recipient require(UUID id) throws InvalidRecipientException {
        return find(id).orElseThrow(() -> new InvalidRecipientException("Penerima tidak ditemukan"));
    }

    private void applyDefault(Recipient chosen) {
        for (Recipient r : recipients) r.markDefault(r == chosen);
    }

    private void ensureOneDefault() {
        if (recipients.isEmpty()) return;
        long defaults = recipients.stream().filter(Recipient::isDefault).count();
        if (defaults != 1) applyDefault(recipients.stream().filter(Recipient::isDefault).findFirst().orElse(recipients.get(0)));
    }

    private void save() {
        autoSave.save(recipients.stream().map(RecipientDto::from).toList());
        listeners.forEach(Runnable::run);
    }
}
