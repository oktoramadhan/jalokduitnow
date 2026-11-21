package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.Expense;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.schema.*;
import com.okto.jalokduitnow.service.SchemaService.TypeChangeImpact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** What the Columns page relies on: add / rename / reorder / hide / delete, option edits, type changes, persistence. */
class ColumnManagementTest {
    @TempDir Path dir;
    Fixture fx;
    SchemaService schema;
    FinanceService finance;

    @BeforeEach
    void setUp() throws Exception {
        fx = Fixture.in(dir);
        schema = fx.schema;
        finance = fx.finance;
    }

    private Transaction addWith(ColumnDefinition col, Object value) throws Exception {
        Transaction t = new Expense(LocalDate.of(2026, 11, 16), fx.expense("Makan"), 1_000L);
        t.setCustomValue(col, value);
        finance.add(t);
        return t;
    }

    private ColumnDefinition choice(String name, String... options) throws Exception {
        return schema.addColumn(name, new ChoiceType(List.of(options)), false);
    }

    private List<String> names() {
        return schema.getSchema().getColumns().stream().map(ColumnDefinition::getName).toList();
    }

    // ---- add / rename / reorder / hide / delete ----

    @Test
    void addRenameReorderHideDelete() throws Exception {
        ColumnDefinition a = schema.addColumn("Alpha", new TextType(), false);
        ColumnDefinition b = schema.addColumn("Beta", new NumberType(), true);
        assertEquals(List.of("Keterangan", "Alpha", "Beta"), names());
        assertTrue(b.isRequired());

        schema.updateColumn(a.getId(), "Alfa", true);
        assertEquals("Alfa", a.getName());
        assertTrue(a.isRequired());

        schema.move(b.getId(), 0);
        assertEquals(List.of("Beta", "Keterangan", "Alfa"), names());
        assertEquals(List.of(0, 1, 2), schema.getSchema().getColumns().stream().map(ColumnDefinition::getOrder).toList());

        schema.setVisible(a.getId(), false);
        assertEquals(2, schema.getSchema().visibleColumns().size());
        assertFalse(schema.getSchema().visibleColumns().contains(a));

        schema.deleteColumn(b.getId(), false);                 // no data: no confirmation needed
        assertEquals(List.of("Keterangan", "Alfa"), names());
    }

    @Test
    void everyChangeNotifiesListeners() throws Exception {
        int[] calls = {0};
        schema.addListener(() -> calls[0]++);
        ColumnDefinition a = schema.addColumn("A", new TextType(), false);
        schema.updateColumn(a.getId(), "B", false);
        schema.move(a.getId(), 0);
        schema.setVisible(a.getId(), false);
        schema.deleteColumn(a.getId(), false);
        assertEquals(5, calls[0]);                              // the table and the form rebuild on each of these
    }

    // ---- validation messages ----

    @Test
    void duplicateAndEmptyNamesAreRefusedWithoutChangingAnything() throws Exception {
        ColumnDefinition a = schema.addColumn("Alpha", new TextType(), false);
        schema.addColumn("Beta", new TextType(), false);

        assertThrows(InvalidColumnException.class, () -> schema.addColumn("alpha", new TextType(), false));
        assertThrows(InvalidColumnException.class, () -> schema.addColumn("  ", new TextType(), false));
        InvalidColumnException dup = assertThrows(InvalidColumnException.class, () -> schema.updateColumn(a.getId(), "BETA", true));
        assertTrue(dup.getMessage().contains("sudah dipakai"));
        assertEquals("Alpha", a.getName());
        assertFalse(a.isRequired(), "a refused edit must not apply the other fields either");
        assertEquals(3, schema.getSchema().getColumns().size());

        schema.updateColumn(a.getId(), "ALPHA", false);        // only the case changes: that is the same column
        assertEquals("ALPHA", a.getName());
    }

    @Test
    void aChoiceColumnNeedsAtLeastOneOption() {
        assertThrows(InvalidColumnException.class, () -> choice("Jenis"));
        assertThrows(InvalidColumnException.class, () -> schema.addColumn("Jenis", FieldKind.CHOICE.create(List.of("  ")), false));
        assertEquals(1, schema.getSchema().getColumns().size());
    }

    // ---- choice options: usage, rename, removal with remap ----

    @Test
    void optionUsageCountsTransactionsPerOption() throws Exception {
        ColumnDefinition kind = choice("Jenis", "Need", "Want", "Saving");
        addWith(kind, "Need");
        addWith(kind, "Need");
        addWith(kind, "Want");
        assertEquals(Map.of("Need", 2L, "Want", 1L, "Saving", 0L), schema.optionUsage(kind.getId()));
        assertEquals(List.of("Need", "Want", "Saving"), List.copyOf(schema.optionUsage(kind.getId()).keySet()));
        assertThrows(InvalidColumnException.class, () -> schema.optionUsage("keterangan"));   // a Text column
    }

    @Test
    void removingAnOptionInUseNeedsAReplacementAndLosesNothing() throws Exception {
        ColumnDefinition kind = choice("Jenis", "Need", "Want");
        Transaction t = addWith(kind, "Want");

        InvalidColumnException e = assertThrows(InvalidColumnException.class,
                () -> schema.updateChoiceOptions(kind.getId(), List.of("Need"), Map.of()));
        assertTrue(e.getMessage().contains("Want"));
        assertTrue(e.getMessage().contains("1 transaksi"));
        assertEquals(List.of("Need", "Want"), kind.getType().options());   // refused: column untouched
        assertEquals("Want", t.getCustomValue(kind.getId()));              // refused: value untouched
    }

    @Test
    void removingAnOptionRemapsItsValuesToTheChosenOption() throws Exception {
        ColumnDefinition kind = choice("Jenis", "Need", "Want", "Other");
        Transaction want = addWith(kind, "Want");
        Transaction need = addWith(kind, "Need");

        schema.updateChoiceOptions(kind.getId(), List.of("Need", "Other"), Map.of("Want", "Other"));

        assertEquals(List.of("Need", "Other"), kind.getType().options());
        assertEquals("Other", want.getCustomValue(kind.getId()));          // moved to the replacement
        assertEquals("Need", need.getCustomValue(kind.getId()));           // untouched
        assertEquals(Map.of("Need", 1L, "Other", 1L), schema.optionUsage(kind.getId()));
    }

    @Test
    void removingAnUnusedOptionNeedsNoReplacement() throws Exception {
        ColumnDefinition kind = choice("Jenis", "Need", "Want", "Saving");
        addWith(kind, "Need");
        schema.updateChoiceOptions(kind.getId(), List.of("Need", "Want"), Map.of());
        assertEquals(List.of("Need", "Want"), kind.getType().options());
    }

    @Test
    void renamingAnOptionCarriesItsValuesAlong() throws Exception {
        ColumnDefinition kind = choice("Jenis", "Need", "Want");
        Transaction t = addWith(kind, "Want");

        schema.updateChoiceOptions(kind.getId(), List.of("Need", "Keinginan"), Map.of("Want", "Keinginan"));

        assertEquals("Keinginan", t.getCustomValue(kind.getId()));
        assertEquals(List.of("Need", "Keinginan"), kind.getType().options());
    }

    @Test
    void reorderingOptionsKeepsValuesAndChangesOnlyTheOrder() throws Exception {
        ColumnDefinition kind = choice("Jenis", "Need", "Want");
        Transaction t = addWith(kind, "Want");
        schema.updateChoiceOptions(kind.getId(), List.of("Want", "Need"), Map.of());
        assertEquals(List.of("Want", "Need"), kind.getType().options());
        assertEquals("Want", t.getCustomValue(kind.getId()));
    }

    @Test
    void invalidOptionListsAreRefusedBeforeAnythingChanges() throws Exception {
        ColumnDefinition kind = choice("Jenis", "Need", "Want");
        Transaction t = addWith(kind, "Need");
        assertThrows(InvalidColumnException.class, () -> schema.updateChoiceOptions(kind.getId(), List.of(), Map.of()));
        assertThrows(InvalidColumnException.class, () -> schema.updateChoiceOptions(kind.getId(), List.of("A", "a"), Map.of("Need", "A")));
        assertEquals(List.of("Need", "Want"), kind.getType().options());
        assertEquals("Need", t.getCustomValue(kind.getId()));
    }

    // ---- type changes ----

    @Test
    void numberToTextKeepsEveryValue() throws Exception {
        ColumnDefinition col = schema.addColumn("Qty", new NumberType(), false);
        Transaction t = addWith(col, 1500L);

        TypeChangeImpact impact = schema.previewTypeChange(col.getId(), new TextType());
        assertEquals(1, impact.total());
        assertEquals(0, impact.incompatible());
        schema.changeType(col.getId(), new TextType(), false);          // nothing is lost: no confirmation needed
        assertEquals("1500", t.getCustomValue(col.getId()));
    }

    @Test
    void textToNumberKeepsOnlyValuesThatParseAndReportsTheRest() throws Exception {
        ColumnDefinition col = schema.addColumn("Qty", new TextType(), false);
        Transaction good = addWith(col, "2.500");
        Transaction bad1 = addWith(col, "banyak");
        Transaction bad2 = addWith(col, "12 buah");

        TypeChangeImpact impact = schema.previewTypeChange(col.getId(), new NumberType());
        assertEquals(3, impact.total());
        assertEquals(2, impact.incompatible());                          // "will be converted: 1, cleared: 2"
        assertEquals(1, impact.total() - impact.incompatible());

        assertThrows(InvalidColumnException.class, () -> schema.changeType(col.getId(), new NumberType(), false));
        assertEquals("banyak", bad1.getCustomValue(col.getId()));         // refused: nothing changed

        schema.changeType(col.getId(), new NumberType(), true);
        assertEquals(2500L, good.getCustomValue(col.getId()));
        assertNull(bad1.getCustomValue(col.getId()));
        assertNull(bad2.getCustomValue(col.getId()));
    }

    @Test
    void dateYesNoAndChoiceConvertToTextWithoutLoss() throws Exception {
        ColumnDefinition date = schema.addColumn("Tgl", new DateType(), false);
        ColumnDefinition flag = schema.addColumn("Lunas", new BooleanType(), false);
        ColumnDefinition kind = choice("Jenis", "Need", "Want");
        Transaction t = new Expense(LocalDate.of(2026, 11, 16), fx.expense("Makan"), 1_000L);
        t.setCustomValue(date, LocalDate.of(2026, 11, 20));
        t.setCustomValue(flag, true);
        t.setCustomValue(kind, "Want");
        finance.add(t);

        for (ColumnDefinition c : List.of(date, flag, kind)) {
            assertEquals(0, schema.previewTypeChange(c.getId(), new TextType()).incompatible());
            schema.changeType(c.getId(), new TextType(), false);
        }
        assertEquals("2026-11-20", t.getCustomValue(date.getId()));
        assertEquals("true", t.getCustomValue(flag.getId()));
        assertEquals("Want", t.getCustomValue(kind.getId()));
    }

    @Test
    void textToChoiceKeepsOnlyValuesThatAreOptions() throws Exception {
        ColumnDefinition col = schema.addColumn("Jenis", new TextType(), false);
        Transaction ok = addWith(col, "need");                           // matches ignoring case
        Transaction no = addWith(col, "whatever");

        var newType = new ChoiceType(List.of("Need", "Want"));
        assertEquals(1, schema.previewTypeChange(col.getId(), newType).incompatible());
        schema.changeType(col.getId(), newType, true);
        assertEquals("Need", ok.getCustomValue(col.getId()));
        assertNull(no.getCustomValue(col.getId()));
    }

    @Test
    void changingTheTypeOfAnEmptyColumnNeedsNoConfirmation() throws Exception {
        ColumnDefinition col = schema.addColumn("Qty", new TextType(), false);
        assertEquals(0, schema.previewTypeChange(col.getId(), new NumberType()).total());
        schema.changeType(col.getId(), new NumberType(), false);
        assertInstanceOf(NumberType.class, col.getType());
    }

    // ---- delete / hide ----

    @Test
    void deleteReportsHowManyValuesAreAtStakeAndHideKeepsThemAll() throws Exception {
        ColumnDefinition col = schema.addColumn("Catatan", new TextType(), false);
        Transaction a = addWith(col, "x");
        addWith(col, "y");

        assertEquals(2, schema.previewDelete(col.getId()));
        assertThrows(InvalidColumnException.class, () -> schema.deleteColumn(col.getId(), false));

        schema.setVisible(col.getId(), false);                           // the safe alternative
        assertEquals("x", a.getCustomValue(col.getId()));
        schema.setVisible(col.getId(), true);
        assertEquals(2, schema.previewDelete(col.getId()));

        schema.deleteColumn(col.getId(), true);
        assertNull(a.getCustomValue(col.getId()));
    }

    // ---- persistence ----

    @Test
    void allChangesSurviveARestart() throws Exception {
        ColumnDefinition kind = choice("Jenis", "Need", "Want", "Other");
        ColumnDefinition note = schema.addColumn("Catatan", new TextType(), true);
        Transaction t = addWith(kind, "Want");
        schema.updateChoiceOptions(kind.getId(), List.of("Want", "Other"), Map.of("Need", "Want"));
        schema.move(note.getId(), 0);
        schema.setVisible(note.getId(), false);

        Fixture again = fx.reload();
        var cols = again.schema.getSchema().getColumns();
        assertEquals(List.of("Catatan", "Keterangan", "Jenis"), cols.stream().map(ColumnDefinition::getName).toList());
        ColumnDefinition note2 = again.schema.getSchema().require(note.getId());
        assertFalse(note2.isVisible());
        assertTrue(note2.isRequired());
        ColumnDefinition kind2 = again.schema.getSchema().require(kind.getId());
        assertEquals(List.of("Want", "Other"), kind2.getType().options());
        assertEquals("Want", again.finance.getAll().get(0).getCustomValue(kind.getId()));
        assertEquals(t.getId(), again.finance.getAll().get(0).getId());
    }

    @Test
    void thePreviousFilesAreKeptAsBackupsAfterAChange() throws Exception {
        ColumnDefinition col = schema.addColumn("Catatan", new TextType(), false);
        addWith(col, "isi");
        fx.flush();                                                      // first save: no previous version yet
        assertFalse(Files.exists(dir.resolve("schema.json.bak")));

        schema.updateColumn(col.getId(), "Catatan Baru", false);
        schema.deleteColumn(col.getId(), true);                          // also rewrites the transactions
        fx.flush();

        Path schemaBackup = dir.resolve("schema.json.bak");
        Path txBackup = dir.resolve("transactions.json.bak");
        assertTrue(Files.exists(schemaBackup));
        assertTrue(Files.readString(schemaBackup).contains("Catatan"));   // the version before the change
        assertTrue(Files.exists(txBackup));
        assertTrue(Files.readString(txBackup).contains("isi"));           // the value is still recoverable
    }
}
