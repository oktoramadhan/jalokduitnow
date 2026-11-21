package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TableSchemaTest {
    private static ColumnDefinition col(String id, String name, FieldType<?> type, boolean required) throws Exception {
        return new ColumnDefinition(id, name, type, required);
    }

    @Test
    void namesMustBeUniqueIgnoringCase() throws Exception {
        TableSchema s = new TableSchema();
        s.add(col("a", "Catatan", new TextType(), false));
        assertThrows(InvalidColumnException.class, () -> s.add(col("b", "catatan", new TextType(), false)));
        s.add(col("b", "Lokasi", new TextType(), false));
        assertThrows(InvalidColumnException.class, () -> s.rename("b", "CATATAN"));
        s.rename("b", "Tempat");
        assertEquals("Tempat", s.require("b").getName());
    }

    @Test
    void moveKeepsOrderNumbersConsistent() throws Exception {
        TableSchema s = new TableSchema();
        for (String id : List.of("a", "b", "c")) s.add(col(id, id, new TextType(), false));
        s.move("c", 0);
        assertEquals(List.of("c", "a", "b"), s.getColumns().stream().map(ColumnDefinition::getId).toList());
        assertEquals(List.of(0, 1, 2), s.getColumns().stream().map(ColumnDefinition::getOrder).toList());
        s.move("c", 99); // clamped to the end
        assertEquals("c", s.getColumns().get(2).getId());
        s.remove("a");
        assertEquals(List.of(0, 1), s.getColumns().stream().map(ColumnDefinition::getOrder).toList());
    }

    @Test
    void applyInputEnforcesRequiredAndCanonicalizes() throws Exception {
        TableSchema s = new TableSchema();
        s.add(col("kind", "Jenis", new ChoiceType(List.of("Need", "Want")), true));
        s.add(col("qty", "Jumlah Item", new NumberType(), false));

        Map<String, Object> ok = s.applyInput(Map.of(), Map.of("kind", "want", "qty", "1.500"));
        assertEquals(Map.of("kind", "Want", "qty", 1500L), ok);

        assertThrows(InvalidTransactionException.class, () -> s.applyInput(Map.of(), Map.of("qty", "1")));
        assertThrows(InvalidTransactionException.class, () -> s.applyInput(Map.of(), Map.of("kind", "Other")));
    }

    @Test
    void hiddenColumnsAreNotValidatedAndKeepTheirValues() throws Exception {
        TableSchema s = new TableSchema();
        s.add(col("kind", "Jenis", new ChoiceType(List.of("Need", "Want")), true));
        s.require("kind").setVisible(false);
        Map<String, Object> result = s.applyInput(Map.of("kind", "Need"), Map.of());
        assertEquals(Map.of("kind", "Need"), result);
    }

    @Test
    void blankInputClearsOptionalValue() throws Exception {
        TableSchema s = new TableSchema();
        s.add(col("note", "Catatan", new TextType(), false));
        assertTrue(s.applyInput(Map.of("note", "halo"), Map.of("note", " ")).isEmpty());
    }
}
