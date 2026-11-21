package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.okto.jalokduitnow.model.CategoryResolver;
import com.okto.jalokduitnow.model.CategoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JsonRepositoryTest {
    private static final UUID CAT = UUID.randomUUID();
    /** Accepts every id as-is (these tests are about custom columns, not categories). */
    private static final CategoryResolver KEEP = new CategoryResolver() {
        @Override public UUID idFor(CategoryType type, String name) { return CAT; }
        @Override public UUID resolve(UUID id, CategoryType type) { return id; }
    };

    private static TableSchema schema() throws Exception {
        TableSchema s = new TableSchema();
        s.add(new ColumnDefinition("kind", "Jenis", new ChoiceType(List.of("Need", "Want")), true));
        s.add(new ColumnDefinition("qty", "Qty", new NumberType(), false));
        s.add(new ColumnDefinition("when", "Kapan", new DateType(), false));
        s.add(new ColumnDefinition("paid", "Lunas", new BooleanType(), false));
        s.add(new ColumnDefinition("note", "Catatan", new TextType(), false));
        s.require("note").setVisible(false);
        return s;
    }

    @Test
    void schemaRoundTripsEveryFieldTypeAndFlag(@TempDir Path dir) throws Exception {
        TableSchema original = schema();
        var repo = new JsonRepository<>(dir.resolve("schema.json"), ColumnDto.class);
        repo.saveAll(original.getColumns().stream().map(ColumnDto::from).toList());

        TableSchema back = new TableSchema();
        for (ColumnDto dto : repo.findAll()) back.add(dto.toColumn());

        assertEquals(original.getColumns().size(), back.getColumns().size());
        for (int i = 0; i < original.getColumns().size(); i++) {
            ColumnDefinition a = original.getColumns().get(i), b = back.getColumns().get(i);
            assertEquals(a.getId(), b.getId());
            assertEquals(a.getName(), b.getName());
            assertEquals(a.isVisible(), b.isVisible());
            assertEquals(a.isRequired(), b.isRequired());
            assertEquals(a.getType().getClass(), b.getType().getClass()); // polymorphism preserved
            assertEquals(a.getType().options(), b.getType().options());
        }
    }

    @Test
    void transactionsRoundTripTypedCustomValues(@TempDir Path dir) throws Exception {
        TableSchema schema = schema();
        var repo = new JsonRepository<>(dir.resolve("tx.json"), TransactionDto.class);
        assertTrue(repo.findAll().isEmpty());

        Transaction t = new Expense(LocalDate.of(2025, 8, 3), CAT, 25_000L);
        t.replaceCustomValues(schema, Map.of("kind", "Need", "qty", 3L,
                "when", LocalDate.of(2025, 8, 2), "paid", true));
        repo.saveAll(List.of(TransactionDto.from(t, schema)));

        Transaction back = repo.findAll().get(0).toTransaction(schema, KEEP);
        assertEquals(t.getId(), back.getId());
        assertEquals(-25_000L, back.signedAmount());
        assertEquals(LocalDate.of(2025, 8, 3), back.getDate());
        assertEquals(t.getCustomValues(), back.getCustomValues()); // same typed values (String/Long/LocalDate/Boolean)
    }

    @Test
    void valuesOfDeletedColumnsAreDroppedOnLoad(@TempDir Path dir) throws Exception {
        TableSchema schema = schema();
        Transaction t = new Expense(LocalDate.of(2025, 8, 3), CAT, 1_000L);
        t.replaceCustomValues(schema, Map.of("qty", 3L));
        TransactionDto dto = TransactionDto.from(t, schema);

        schema.remove("qty");
        assertTrue(dto.toTransaction(schema, KEEP).getCustomValues().isEmpty());
    }

    @Test
    void legacyDescriptionMovesIntoKeteranganColumn(@TempDir Path dir) throws Exception {
        TableSchema schema = new TableSchema();
        schema.add(new ColumnDefinition("keterangan", "Keterangan", new TextType(), false));
        Path file = dir.resolve("old.json");
        Files.writeString(file, """
                [{"id":"%s","type":"Expense","date":"2025-08-03","category":"Makan","amount":25000,"description":"nasi"}]
                """.formatted(UUID.randomUUID()));
        Transaction t = new JsonRepository<>(file, TransactionDto.class).findAll().get(0).toTransaction(schema, KEEP);
        assertEquals("nasi", t.getCustomValue("keterangan"));
    }

    @Test
    void corruptFileThrowsAndLeavesABackupCopy(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("tx.json");
        Files.writeString(file, "{ this is not json");
        var repo = new JsonRepository<>(file, TransactionDto.class);

        assertThrows(StorageException.class, repo::findAll);
        assertEquals("{ this is not json", Files.readString(file)); // original untouched
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> backups = files.filter(p -> p.getFileName().toString().contains(".corrupt-")).toList();
            assertEquals(1, backups.size());
            assertEquals("{ this is not json", Files.readString(backups.get(0)));
        }
    }

    @Test
    void savingKeepsThePreviousVersionAsBak(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("tx.json");
        var repo = new JsonRepository<>(file, TransactionDto.class);
        TableSchema schema = schema();
        Transaction a = new Income(LocalDate.of(2025, 8, 1), CAT, 1L);
        repo.saveAll(List.of(TransactionDto.from(a, schema)));
        assertFalse(Files.exists(dir.resolve("tx.json.bak")));

        repo.saveAll(List.of());
        assertTrue(Files.exists(dir.resolve("tx.json.bak")));
        assertTrue(Files.readString(dir.resolve("tx.json.bak")).contains(CAT.toString()));
        assertTrue(repo.findAll().isEmpty());
    }
}
