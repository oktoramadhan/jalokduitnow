package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidRecipientException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.Recipient;
import com.okto.jalokduitnow.storage.DonorSettingsDto;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.RecipientDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecipientServiceTest {
    @TempDir Path dir;
    RecipientService service;

    @BeforeEach
    void setUp() throws Exception {
        service = newService();
        service.load();
    }

    private RecipientService newService() {
        return new RecipientService(new JsonRepository<>(dir.resolve("recipients.json"), RecipientDto.class));
    }

    private List<String> defaults() {
        return service.getAll().stream().filter(Recipient::isDefault).map(Recipient::getName).toList();
    }

    @Test
    void theFirstRecipientBecomesTheDefault() throws Exception {
        service.add("Pak Budi", "081234567890", "", false);
        assertEquals(List.of("Pak Budi"), defaults());
        service.add("Bu Sari", "081298765432", "", false);
        assertEquals(List.of("Pak Budi"), defaults());           // a second one does not take over
    }

    @Test
    void settingADefaultClearsTheOldOneSoThereIsExactlyOne() throws Exception {
        service.add("Pak Budi", "081234567890", "", false);
        Recipient sari = service.add("Bu Sari", "081298765432", "", true);   // asked to be the default
        assertEquals(List.of("Bu Sari"), defaults());

        service.setDefault(service.getAll().get(0).getId());
        assertEquals(List.of("Pak Budi"), defaults());
        assertEquals(sari.getId(), service.getAll().get(1).getId());
        assertEquals("Pak Budi", service.getDefault().orElseThrow().getName());
    }

    @Test
    void deletingTheDefaultHandsTheRoleToTheNextRecipient() throws Exception {
        Recipient budi = service.add("Pak Budi", "081234567890", "", false);
        service.add("Bu Sari", "081298765432", "", false);
        service.delete(budi.getId());
        assertEquals(List.of("Bu Sari"), defaults());
    }

    @Test
    void deletingTheLastRecipientLeavesNoDefault() throws Exception {
        Recipient budi = service.add("Pak Budi", "081234567890", "", false);
        service.delete(budi.getId());
        assertTrue(service.getAll().isEmpty());
        assertTrue(service.getDefault().isEmpty());
    }

    @Test
    void updateValidatesAndKeepsTheOldValuesOnFailure() throws Exception {
        Recipient budi = service.add("Pak Budi", "081234567890", "catatan", false);
        assertThrows(InvalidRecipientException.class, () -> service.update(budi.getId(), "Budi", "12", ""));
        assertEquals("Pak Budi", budi.getName());
        service.update(budi.getId(), "Budi Santoso", "0813-1111-2222", "baru");
        assertEquals("Budi Santoso", budi.getName());
        assertEquals("62813111122", budi.getPhoneNumber().substring(0, 11));
        assertThrows(InvalidRecipientException.class, () -> service.update(UUID.randomUUID(), "X", "081234567890", ""));
        assertThrows(InvalidRecipientException.class, () -> service.delete(UUID.randomUUID()));
        assertThrows(InvalidRecipientException.class, () -> service.setDefault(UUID.randomUUID()));
    }

    @Test
    void invalidInputOnAddIsRefusedAndAddsNothing() {
        assertThrows(InvalidRecipientException.class, () -> service.add("", "081234567890", "", false));
        assertThrows(InvalidRecipientException.class, () -> service.add("Budi", "abc", "", false));
        assertTrue(service.getAll().isEmpty());
    }

    @Test
    void changesNotifyListeners() throws Exception {
        int[] calls = {0};
        service.addListener(() -> calls[0]++);
        Recipient r = service.add("Budi", "081234567890", "", false);
        service.update(r.getId(), "Budi 2", "081234567890", "");
        service.setDefault(r.getId());
        service.delete(r.getId());
        assertEquals(4, calls[0]);
    }

    // ---- persistence ----

    @Test
    void recipientsSurviveARestartWithTheirNormalizedNumbersNotesAndDefault() throws Exception {
        service.add("Pak Budi", "0812-3456-7890", "donatur bulanan", false);
        Recipient sari = service.add("Bu Sari", "+62 812 9876 5432", "", true);
        service.flush();

        RecipientService again = newService();
        again.load();
        assertEquals(2, again.getAll().size());
        Recipient budi = again.getAll().get(0);
        assertEquals("Pak Budi", budi.getName());
        assertEquals("6281234567890", budi.getPhoneNumber());
        assertEquals("donatur bulanan", budi.getNote());
        assertFalse(budi.isDefault());
        Recipient loadedSari = again.find(sari.getId()).orElseThrow();
        assertEquals("628129876543", loadedSari.getPhoneNumber().substring(0, 12));
        assertTrue(loadedSari.isDefault());
        assertEquals(loadedSari.getId(), again.getDefault().orElseThrow().getId());
    }

    @Test
    void theFileStoresTheNormalizedNumber() throws Exception {
        service.add("Pak Budi", "0812 3456 7890", "", false);
        service.flush();
        String json = Files.readString(dir.resolve("recipients.json"));
        assertTrue(json.contains("6281234567890"));
        assertFalse(json.contains("0812"));
    }

    @Test
    void aFileWithNoDefaultGetsOneOnLoad() throws Exception {
        Files.writeString(dir.resolve("recipients.json"), """
                [{"id":"%s","name":"A","phoneNumber":"6281234567890","note":"","defaultRecipient":false},
                 {"id":"%s","name":"B","phoneNumber":"6281298765432","note":"","defaultRecipient":false}]
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));
        RecipientService fresh = newService();
        fresh.load();
        assertEquals("A", fresh.getDefault().orElseThrow().getName());
    }

    @Test
    void aCorruptFileThrowsStorageExceptionAndKeepsACopy() throws Exception {
        Files.writeString(dir.resolve("recipients.json"), "{ not json");
        RecipientService broken = newService();
        assertThrows(StorageException.class, broken::load);
        try (var files = Files.list(dir)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    @Test
    void aFileWithAnInvalidNumberIsReportedAsStorageError() throws Exception {
        Files.writeString(dir.resolve("recipients.json"), """
                [{"id":"%s","name":"A","phoneNumber":"abc","note":"","defaultRecipient":false}]
                """.formatted(UUID.randomUUID()));
        assertThrows(StorageException.class, newService()::load);
    }

    // ---- donor settings ----

    @Test
    void donorCategoriesRoundTrip() throws Exception {
        var settings = new DonorSettings(new JsonRepository<>(dir.resolve("donor-settings.json"), DonorSettingsDto.class));
        settings.load();
        assertTrue(settings.donorCategoryIds().isEmpty());

        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        settings.setDonorCategory(a, true);
        settings.setDonorCategory(b, true);
        settings.setDonorCategory(b, false);
        assertTrue(settings.isDonorCategory(a));
        assertFalse(settings.isDonorCategory(b));
        settings.flush();

        var again = new DonorSettings(new JsonRepository<>(dir.resolve("donor-settings.json"), DonorSettingsDto.class));
        again.load();
        assertEquals(java.util.Set.of(a), again.donorCategoryIds());
    }

    @Test
    void donorSettingsNotifyOnlyOnRealChanges() throws Exception {
        var settings = new DonorSettings(new JsonRepository<>(dir.resolve("donor-settings.json"), DonorSettingsDto.class));
        int[] calls = {0};
        settings.addListener(() -> calls[0]++);
        UUID a = UUID.randomUUID();
        settings.setDonorCategory(a, true);
        settings.setDonorCategory(a, true);     // already marked
        settings.setDonorCategory(UUID.randomUUID(), false);   // was never marked
        assertEquals(1, calls[0]);
    }
}
