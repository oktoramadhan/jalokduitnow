package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.InvalidRecipientException;
import com.okto.jalokduitnow.model.Recipient;

import java.util.UUID;

/** JSON form of a Recipient (recipients.json). */
public record RecipientDto(UUID id, String name, String phoneNumber, String note, boolean defaultRecipient) {
    public static RecipientDto from(Recipient r) {
        return new RecipientDto(r.getId(), r.getName(), r.getPhoneNumber(), r.getNote(), r.isDefault());
    }

    public Recipient toRecipient() throws InvalidRecipientException {
        return new Recipient(id, name, phoneNumber, note, defaultRecipient);
    }
}
