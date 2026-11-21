package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.ChoiceType;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.storage.*;
import com.okto.jalokduitnow.storage.CsvExporter.Delimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {
    @TempDir Path dir;
    TestClock clock = new TestClock(LocalDate.of(2026, 11, 21));
    Fixture fx;
    GameService game;
    RecipientService recipients;
    DonorSettings donors;
    BackupService backup;
    UUID food, donation;
    ColumnDefinition kind;

    @BeforeEach
    void setUp() throws Exception {
        fx = Fixture.in(dir, clock);
        GameEventBus bus = new GameEventBus();
        fx.finance.setEventBus(bus);
        game = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                fx.finance, fx.categories, fx.schema, bus, new XpPolicy(), clock);
        game.load();
        recipients = new RecipientService(new JsonRepository<>(dir.resolve("recipients.json"), RecipientDto.class));
        recipients.load();
        donors = new DonorSettings(new JsonRepository<>(dir.resolve("donor-settings.json"), DonorSettingsDto.class));
        donors.load();
        backup = new BackupService(fx.finance, fx.schema, fx.categories, game, recipients, donors, clock);

        food = fx.expense("Makan");
        donation = fx.income("Donatur");
        kind = fx.schema.addColumn("Jenis", new ChoiceType(List.of("Need", "Want")), false);
        donors.setDonorCategory(donation, true);
        recipients.add("Pak Budi", "0812-3456-7890", "donatur", true);

        Transaction a = new Expense(LocalDate.of(2026, 11, 3), food, 25_000);
        a.setCustomValue(kind, "Want");
        a.setCustomValue(fx.schema.getSchema().require("keterangan"), "nasi, ayam");
        fx.finance.add(a);
        fx.finance.add(new Income(LocalDate.of(2026, 10, 1), donation, 3_000_000));
    }

    // ---- CSV through the service ----

    @Test
    void csvCanBeLimitedToOneMonth() {
        String all = backup.transactionsCsv(null, Delimiter.COMMA);
        String nov = backup.transactionsCsv(YearMonth.of(2026, 11), Delimiter.COMMA);
        assertEquals(3, all.split("\r\n").length);                      // header + 2 transactions
        assertEquals(2, nov.split("\r\n").length);                      // header + 1
        assertTrue(nov.contains("2026-11-03,Expense,Makan,25000"));
        assertFalse(nov.contains("Donatur"));
        assertEquals(1, backup.transactionsFor(YearMonth.of(2026, 10)).size());
        assertEquals(2, backup.transactionsFor(null).size());
        assertEquals(1, backup.transactionsCsv(YearMonth.of(2026, 9), Delimiter.COMMA).split("\r\n").length);   // empty month: header only
    }

    // ---- JSON backup ----

    @Test
    void theBackupHasAHeaderAndEveryKindOfData() throws Exception {
        game.flush();
        BackupDto b = backup.snapshot(true);
        assertEquals("JalokDuitNow", b.app());
        assertEquals(BackupDto.CURRENT_VERSION, b.formatVersion());
        assertEquals(clock.instant(), b.exportedAt());
        assertEquals(2, b.columns().size());                            // Keterangan + Jenis
        assertTrue(b.categories().stream().anyMatch(c -> c.name().equals("Makan")));
        assertEquals(2, b.transactions().size());
        assertNotNull(b.game().profile());
        assertEquals(1, b.recipients().size());
        assertEquals(List.of(donation), b.donorCategoryIds());
    }

    @Test
    void aBackupSurvivesJsonAndReadsBackAsTheSameData() throws Exception {
        String json = backup.backupJson(true);
        BackupDto back = BackupService.parse(json);
        BackupDto original = backup.snapshot(true);

        assertEquals(original.transactions(), back.transactions());
        assertEquals(original.categories(), back.categories());
        assertEquals(original.recipients(), back.recipients());
        assertEquals(original.donorCategoryIds(), back.donorCategoryIds());
        assertEquals(original.columns().size(), back.columns().size());
        assertEquals(original.game().profile(), back.game().profile());
        assertEquals(original.exportedAt(), back.exportedAt());
        // the file is human readable
        assertTrue(json.contains("\"app\" : \"JalokDuitNow\""));
        assertTrue(json.contains("\n"));
    }

    @Test
    void theBackupHoldsEnoughToRebuildTheTransactions() throws Exception {
        BackupDto back = BackupService.parse(backup.backupJson(true));

        TableSchema schema = new TableSchema();                         // rebuilt only from the backup
        for (ColumnDto c : back.columns()) schema.add(c.toColumn());
        var knownCategories = back.categories().stream().map(CategoryDto::id).toList();
        CategoryResolver resolver = new CategoryResolver() {
            @Override public UUID idFor(CategoryType type, String name) { throw new AssertionError("ids are present"); }
            @Override public UUID resolve(UUID id, CategoryType type) { assertTrue(knownCategories.contains(id)); return id; }
        };
        List<Transaction> rebuilt = new java.util.ArrayList<>();
        for (TransactionDto dto : back.transactions()) rebuilt.add(dto.toTransaction(schema, resolver));

        assertEquals(2, rebuilt.size());
        Transaction nasi = rebuilt.stream().filter(t -> t.getAmount() == 25_000).findFirst().orElseThrow();
        assertEquals("Want", nasi.getCustomValue(kind.getId()));
        assertEquals("nasi, ayam", nasi.getCustomValue("keterangan"));
        assertEquals(LocalDate.of(2026, 11, 3), nasi.getDate());
        assertEquals(food, nasi.getCategoryId());
        assertNotNull(nasi.getCreatedAt());
    }

    @Test
    void donorDataIsLeftOutWhenNotIncluded() throws Exception {
        BackupDto b = BackupService.parse(backup.backupJson(false));
        assertTrue(b.recipients().isEmpty());
        assertTrue(b.donorCategoryIds().isEmpty());
        assertEquals(2, b.transactions().size());                       // everything else is still there
        assertFalse(backup.backupJson(false).contains("6281234567890"), "the phone number must not leak");
        assertTrue(backup.backupJson(true).contains("6281234567890"));
    }

    @Test
    void theBackupDoesNotChangeTheData() throws Exception {
        int tx = fx.finance.getAll().size();
        long xp = game.profile().getXp();
        backup.backupJson(true);
        backup.transactionsCsv(null, Delimiter.SEMICOLON);
        assertEquals(tx, fx.finance.getAll().size());
        assertEquals(xp, game.profile().getXp());
    }

    @Test
    void anEmptyAppStillMakesAValidBackup(@TempDir Path other) throws Exception {
        Fixture empty = Fixture.in(other, clock);
        GameService g = new GameService(new JsonRepository<>(other.resolve("game.json"), GameStateDto.class),
                empty.finance, empty.categories, empty.schema, new GameEventBus(), new XpPolicy(), clock);
        g.load();
        var r = new RecipientService(new JsonRepository<>(other.resolve("r.json"), RecipientDto.class));
        var d = new DonorSettings(new JsonRepository<>(other.resolve("d.json"), DonorSettingsDto.class));
        BackupDto b = BackupService.parse(new BackupService(empty.finance, empty.schema, empty.categories, g, r, d, clock).backupJson(true));
        assertTrue(b.transactions().isEmpty());
        assertFalse(b.categories().isEmpty());                          // the seeded defaults
    }

    // ---- reading a backup defensively ----

    @Test
    void parseRefusesFilesThatAreNotBackups() {
        assertThrows(StorageException.class, () -> BackupService.parse("not json at all"));
        assertThrows(StorageException.class, () -> BackupService.parse("[]"));
        StorageException other = assertThrows(StorageException.class, () -> BackupService.parse(
                "{\"app\":\"SomethingElse\",\"formatVersion\":1}"));
        assertTrue(other.getMessage().contains("Bukan file cadangan"));
    }

    @Test
    void parseRefusesABackupFromANewerVersion() {
        StorageException e = assertThrows(StorageException.class, () -> BackupService.parse(
                "{\"app\":\"JalokDuitNow\",\"formatVersion\":999}"));
        assertTrue(e.getMessage().contains("lebih baru"));
    }

    @Test
    void theBackupFileIsWrittenAndReadBackFromDisk() throws Exception {
        Path file = ExportFile.write(dir.resolve("backups/cadangan.json"), backup.backupJson(true), false);
        BackupDto back = BackupService.parse(java.nio.file.Files.readString(file));
        assertEquals(2, back.transactions().size());
    }
}
