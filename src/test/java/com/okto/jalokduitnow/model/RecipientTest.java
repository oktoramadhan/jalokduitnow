package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidRecipientException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecipientTest {
    @Test
    void storesTheNormalizedNumberAndTrimmedText() throws Exception {
        Recipient r = Recipient.create("  Pak Budi ", "0812-3456-7890", "  donatur bulanan ");
        assertEquals("Pak Budi", r.getName());
        assertEquals("6281234567890", r.getPhoneNumber());
        assertEquals("+6281234567890", r.displayPhone());
        assertEquals("donatur bulanan", r.getNote());
        assertFalse(r.isDefault());
        assertNotNull(r.getId());
    }

    @Test
    void invalidInputIsRefusedAndNothingChanges() throws Exception {
        Recipient r = Recipient.create("Pak Budi", "081234567890", "catatan");
        assertThrows(InvalidRecipientException.class, () -> r.update("  ", "081234567890", ""));
        assertThrows(InvalidRecipientException.class, () -> r.update("Budi", "abc", ""));
        assertThrows(InvalidRecipientException.class, () -> r.update("x".repeat(Recipient.MAX_NAME_LENGTH + 1), "081234567890", ""));
        assertThrows(InvalidRecipientException.class, () -> r.update("Budi", "081234567890", "x".repeat(Recipient.MAX_NOTE_LENGTH + 1)));
        assertEquals("Pak Budi", r.getName());                 // all or nothing
        assertEquals("6281234567890", r.getPhoneNumber());
        assertEquals("catatan", r.getNote());
    }

    @Test
    void aMissingNoteBecomesEmptyAndAnIdIsRequired() throws Exception {
        assertEquals("", Recipient.create("Budi", "081234567890", null).getNote());
        assertThrows(InvalidRecipientException.class, () -> new Recipient(null, "Budi", "081234567890", "", false));
    }

    @Test
    void updateReNormalizesTheNumber() throws Exception {
        Recipient r = new Recipient(UUID.randomUUID(), "Budi", "081234567890", "", true);
        r.update("Budi S", "+62 811 2223 3344", "baru");
        assertEquals("6281122233344", r.getPhoneNumber());
        assertEquals("Budi S", r.getName());
        assertTrue(r.isDefault());
    }
}
