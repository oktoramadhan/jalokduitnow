package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidRecipientException;
import com.okto.jalokduitnow.util.PhoneNumberNormalizer;

import java.util.UUID;

/**
 * Someone a report can be sent to (for example a donor). Encapsulated: the phone number is always stored in its
 * normalized, digits-only form, and name and number are validated together in {@link #update}.
 * Which recipient is the default is decided by RecipientService (only one can be).
 */
public class Recipient {
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_NOTE_LENGTH = 200;

    private final UUID id;
    private String name;
    private String phoneNumber;     // normalized, e.g. "6281234567890"
    private String note;
    private boolean defaultRecipient;

    /** @param rawPhone the number as typed; it is normalized here */
    public Recipient(UUID id, String name, String rawPhone, String note, boolean isDefault)
            throws InvalidRecipientException {
        if (id == null) throw new InvalidRecipientException("Id penerima wajib");
        this.id = id;
        update(name, rawPhone, note);
        this.defaultRecipient = isDefault;
    }

    public static Recipient create(String name, String rawPhone, String note) throws InvalidRecipientException {
        return new Recipient(UUID.randomUUID(), name, rawPhone, note, false);
    }

    /** Changes name, phone number and note together; if anything is invalid nothing changes. */
    public void update(String name, String rawPhone, String note) throws InvalidRecipientException {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) throw new InvalidRecipientException("Nama penerima wajib diisi");
        if (cleanName.length() > MAX_NAME_LENGTH) {
            throw new InvalidRecipientException("Nama penerima maksimal " + MAX_NAME_LENGTH + " karakter");
        }
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > MAX_NOTE_LENGTH) {
            throw new InvalidRecipientException("Catatan maksimal " + MAX_NOTE_LENGTH + " karakter");
        }
        String normalized = PhoneNumberNormalizer.normalize(rawPhone);
        this.name = cleanName;
        this.phoneNumber = normalized;
        this.note = cleanNote;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getNote() { return note; }
    public boolean isDefault() { return defaultRecipient; }

    /** Public only because RecipientService (another package) calls it; the service is what keeps "exactly one default" true. */
    public void markDefault(boolean value) { this.defaultRecipient = value; }

    /** "+6281234567890" for showing to the user. */
    public String displayPhone() { return "+" + phoneNumber; }

    @Override
    public String toString() { return name; }
}
