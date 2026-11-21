package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.*;
import com.okto.jalokduitnow.service.SchemaService.TypeChangeImpact;
import com.okto.jalokduitnow.storage.ColumnDto;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.TransactionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaServiceTest {
    @TempDir Path dir;
    Fixture fx;
    FinanceService finance;
    SchemaService schema;

    @BeforeEach
    void setUp() throws Exception {
        fx = Fixture.in(dir);
        finance = fx.finance;
        schema = fx.schema;
    }

    private Transaction addWith(ColumnDefinition col, Object value) throws Exception {
        Transaction t = new Expense(LocalDate.of(2025, 9, 1), fx.expense("Makan"), 1_000L);
        t.setCustomValue(col, value);
        finance.add(t);
        return t;
    }

    @Test
    void firstRunSeedsKeteranganColumn() {
        assertEquals(1, schema.getSchema().getColumns().size());
        assertEquals("Keterangan", schema.getSchema().getColumns().get(0).getName());
    }

    @Test
    void schemaSurvivesReload() throws Exception {
        schema.addColumn("Jenis", new ChoiceType(List.of("Need", "Want")), true);
        schema.flush();
        var again = new SchemaService(new JsonRepository<>(dir.resolve("schema.json"), ColumnDto.class), finance);
        again.load();
        var cols = again.getSchema().getColumns();
        assertEquals(2, cols.size());
        assertEquals("Jenis", cols.get(1).getName());
        assertTrue(cols.get(1).isRequired());
        assertEquals(List.of("Need", "Want"), cols.get(1).getType().options());
    }

    @Test
    void deleteWithDataNeedsConfirmation() throws Exception {
        ColumnDefinition col = schema.addColumn("Jenis", new TextType(), false);
        Transaction t = addWith(col, "Need");

        assertEquals(1, schema.previewDelete(col.getId()));
        assertThrows(InvalidColumnException.class, () -> schema.deleteColumn(col.getId(), false));
        assertTrue(schema.getSchema().find(col.getId()).isPresent());   // refused: nothing was lost
        assertEquals("Need", t.getCustomValue(col.getId()));

        schema.deleteColumn(col.getId(), true);
        assertTrue(schema.getSchema().find(col.getId()).isEmpty());
        assertNull(t.getCustomValue(col.getId()));
    }

    @Test
    void deleteWithoutDataNeedsNoConfirmation() throws Exception {
        ColumnDefinition col = schema.addColumn("Kosong", new TextType(), false);
        schema.deleteColumn(col.getId(), false);
        assertTrue(schema.getSchema().find(col.getId()).isEmpty());
    }

    @Test
    void typeChangeConvertsCompatibleValuesAndWarnsAboutTheRest() throws Exception {
        ColumnDefinition col = schema.addColumn("Qty", new TextType(), false);
        Transaction good = addWith(col, "1.500");
        Transaction bad = addWith(col, "banyak");

        TypeChangeImpact impact = schema.previewTypeChange(col.getId(), new NumberType());
        assertEquals(2, impact.total());
        assertEquals(1, impact.incompatible());

        assertThrows(InvalidColumnException.class, () -> schema.changeType(col.getId(), new NumberType(), false));
        assertInstanceOf(TextType.class, col.getType());          // refused: type unchanged
        assertEquals("banyak", bad.getCustomValue(col.getId()));   // refused: data unchanged

        schema.changeType(col.getId(), new NumberType(), true);
        assertInstanceOf(NumberType.class, col.getType());
        assertEquals(1500L, good.getCustomValue(col.getId()));     // now a typed Long
        assertNull(bad.getCustomValue(col.getId()));
    }

    @Test
    void removingAChoiceOptionInUseIsAWarning() throws Exception {
        ColumnDefinition col = schema.addColumn("Jenis", new ChoiceType(List.of("Need", "Want")), false);
        addWith(col, "Want");
        var fewer = new ChoiceType(List.of("Need"));
        assertEquals(1, schema.previewTypeChange(col.getId(), fewer).incompatible());
        assertThrows(InvalidColumnException.class, () -> schema.changeType(col.getId(), fewer, false));
    }

    @Test
    void hidingKeepsValues() throws Exception {
        ColumnDefinition col = schema.addColumn("Jenis", new TextType(), false);
        Transaction t = addWith(col, "Need");
        schema.setVisible(col.getId(), false);
        assertFalse(col.isVisible());
        assertEquals("Need", t.getCustomValue(col.getId()));
    }

    @Test
    void changesReachDiskThroughAutoSave() throws Exception {
        ColumnDefinition col = schema.addColumn("Qty", new NumberType(), false);
        addWith(col, 7L);
        finance.flush();
        schema.flush();

        Fixture again = fx.reload();
        assertEquals(7L, again.finance.getAll().get(0).getCustomValue(col.getId()));
    }
}
