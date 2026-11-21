package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.AppInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.BackupDto;
import com.okto.jalokduitnow.storage.BackupValidator;
import com.okto.jalokduitnow.storage.JsonSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Everything a restore refuses BEFORE it touches any data. */
class BackupValidatorTest {
    @TempDir Path dir;
    ObjectMapper mapper = JsonSupport.newMapper();
    String valid;

    @BeforeEach
    void setUp() throws Exception {
        valid = RestoreWorld.create(dir).seed(5, "A").backupJson(true);
    }

    private String edit(Consumer<ObjectNode> change) throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(valid);
        change.accept(root);
        return mapper.writeValueAsString(root);
    }

    private StorageException refused(String json) {
        return assertThrows(StorageException.class, () -> BackupValidator.validate(json));
    }

    // ---- accepted ----

    @Test
    void aGoodBackupIsAccepted() throws Exception {
        BackupDto b = BackupValidator.validate(valid);
        assertEquals("JalokDuitNow", b.app());
        assertEquals(6, b.transactions().size());
        assertEquals(AppInfo.VERSION, b.appVersion());
        assertTrue(b.hasDonorData());
    }

    @Test
    void anOlderBackupWithoutTheNewerOptionalFieldsIsAccepted() throws Exception {
        String old = edit(r -> { r.remove("appVersion"); r.remove("includesDonorData"); r.remove("reminderSettings"); });
        BackupDto b = BackupValidator.validate(old);
        assertNull(b.appVersion());
        assertTrue(b.hasDonorData(), "an older backup always contained donor data");
        assertNull(b.reminderSettings());
    }

    // ---- not a backup / not JSON ----

    @Test
    void textThatIsNotJsonIsRefused() {
        for (String junk : new String[]{"", "   ", "hello", "{ broken", "{\"app\": ", "<xml/>", "\u0000\u0001\u0002"}) {
            StorageException e = refused(junk);
            assertFalse(e.getMessage().isBlank(), "'" + junk + "' needs a message");
        }
    }

    @Test
    void jsonThatIsNotOurBackupIsRefused() {
        for (String other : new String[]{"[]", "\"text\"", "42", "null", "{}", "{\"app\":\"Other\",\"formatVersion\":1}",
                "{\"formatVersion\":1}", "[{\"id\":1}]"}) {
            assertTrue(refused(other).getMessage().contains("Bukan file cadangan"), other);
        }
    }

    // ---- version ----

    @Test
    void aNewerVersionIsRefusedWithAClearMessageEvenIfTheShapeChanged() throws Exception {
        StorageException e = refused(edit(r -> { r.put("formatVersion", 999); r.put("somethingNew", "x"); r.remove("columns"); }));
        assertTrue(e.getMessage().contains("lebih baru"), e.getMessage());
        assertTrue(e.getMessage().contains("999"));
    }

    @Test
    void aMissingOrInvalidVersionIsRefused() throws Exception {
        assertTrue(refused(edit(r -> r.remove("formatVersion"))).getMessage().contains("Versi"));
        assertTrue(refused(edit(r -> r.put("formatVersion", 0))).getMessage().contains("Versi"));
        assertTrue(refused(edit(r -> r.put("formatVersion", -3))).getMessage().contains("Versi"));
        assertTrue(refused(edit(r -> r.put("formatVersion", "1"))).getMessage().contains("Versi"));
        assertTrue(refused(edit(r -> r.put("formatVersion", 1.5))).getMessage().contains("Versi"));
    }

    // ---- required sections ----

    @Test
    void everyRequiredSectionMustBePresent() throws Exception {
        String[][] sections = {{"columns", "kolom"}, {"categories", "kategori"}, {"transactions", "transaksi"},
                {"game", "game"}, {"recipients", "penerima"}};
        for (String[] s : sections) {
            StorageException removed = refused(edit(r -> r.remove(s[0])));
            assertTrue(removed.getMessage().contains("tidak lengkap") && removed.getMessage().contains(s[1]), s[0] + ": " + removed.getMessage());
            StorageException nulled = refused(edit(r -> r.putNull(s[0])));
            assertTrue(nulled.getMessage().contains("tidak lengkap"), s[0] + " as null");
        }
    }

    @Test
    void theGameProfileAndGoalsAreRequired() throws Exception {
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("game")).remove("profile"))).getMessage().contains("profil game"));
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("game")).remove("goals"))).getMessage().contains("target tabungan"));
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("game")).putNull("profile"))).getMessage().contains("profil game"));
    }

    @Test
    void anEmptyRecipientsListIsFineButMissingIsNot() throws Exception {
        assertDoesNotThrow(() -> BackupValidator.validate(edit(r -> r.set("recipients", mapper.createArrayNode()))));
    }

    // ---- values that cannot be loaded ----

    @Test
    void aTransactionWithAnUnknownCategoryIsRefused() throws Exception {
        String bad = edit(r -> ((ObjectNode) r.get("transactions").get(0)).put("categoryId", UUID.randomUUID().toString()));
        StorageException e = refused(bad);
        assertTrue(e.getMessage().contains("Cadangan rusak") && e.getMessage().contains("kategori"), e.getMessage());
    }

    @Test
    void aTransactionWhoseTypeDoesNotMatchItsCategoryIsRefused() throws Exception {
        String bad = edit(r -> ((ObjectNode) r.get("transactions").get(0)).put("type", "Income"));   // an expense category
        assertTrue(refused(bad).getMessage().contains("tidak cocok"));
    }

    @Test
    void duplicateTransactionIdsAreRefused() throws Exception {
        String bad = edit(r -> {
            ArrayNode tx = (ArrayNode) r.get("transactions");
            ((ObjectNode) tx.get(1)).set("id", tx.get(0).get("id"));
        });
        assertTrue(refused(bad).getMessage().contains("ganda"));
    }

    @Test
    void duplicateCategoryIdsAreRefused() throws Exception {
        String bad = edit(r -> {
            ArrayNode cats = (ArrayNode) r.get("categories");
            ((ObjectNode) cats.get(1)).set("id", cats.get(0).get("id"));
        });
        assertTrue(refused(bad).getMessage().contains("ganda"));
    }

    @Test
    void impossibleValuesAreRefusedWithAPlainMessage() throws Exception {
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("transactions").get(0)).put("amount", 0))).getMessage().contains("rusak"));
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("transactions").get(0)).put("date", "not-a-date"))).getMessage().contains("tidak bisa dibaca"));
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("columns").get(0)).put("name", " "))).getMessage().contains("rusak"));
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("game").get("profile")).put("xp", -5))).getMessage().contains("rusak"));
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("recipients").get(0)).put("phoneNumber", "abc"))).getMessage().contains("rusak"));
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("reminderSettings")).put("donorReportDay", 40))).getMessage().contains("rusak"));
        assertTrue(refused(edit(r -> ((ObjectNode) r.get("categories").get(0)).put("color", "red"))).getMessage().contains("rusak"));
    }

    @Test
    void aNullElementInsideASectionIsRefusedNotACrash() throws Exception {
        assertDoesNotThrow(() -> {
            StorageException e = refused(edit(r -> ((ArrayNode) r.get("transactions")).addNull()));
            assertFalse(e.getMessage().isBlank());
        });
    }

    @Test
    void aTransactionStillInTheOldNameFormatIsRefused() throws Exception {
        String bad = edit(r -> {
            ObjectNode tx = (ObjectNode) r.get("transactions").get(0);
            tx.remove("categoryId");
            tx.put("category", "Makan");
        });
        assertTrue(refused(bad).getMessage().contains("format lama"));
    }

    @Test
    void theErrorsNeverShowAStackTraceOrBlankText() throws Exception {
        for (String bad : new String[]{"x", edit(r -> r.remove("game")), edit(r -> r.put("formatVersion", 99)),
                edit(r -> ((ObjectNode) r.get("transactions").get(0)).put("amount", -1))}) {
            String m = refused(bad).getMessage();
            assertFalse(m.contains("\tat ") || m.contains("Exception"), m);
            assertFalse(m.isBlank());
        }
    }
}
