package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.model.Expense;
import com.okto.jalokduitnow.model.Income;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.schema.*;
import com.okto.jalokduitnow.storage.CsvExporter.Delimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {
    @TempDir Path dir;
    Fixture fx;
    UUID food, salary;
    ColumnDefinition note, kind, qty, when, paid;

    @BeforeEach
    void setUp() throws Exception {
        fx = Fixture.in(dir);
        food = fx.expense("Makan");
        salary = fx.income("Gaji");
        note = fx.schema.getSchema().require("keterangan");           // the default text column
        kind = fx.schema.addColumn("Jenis", new ChoiceType(List.of("Need", "Want")), false);
        qty = fx.schema.addColumn("Qty", new NumberType(), false);
        when = fx.schema.addColumn("Tgl", new DateType(), false);
        paid = fx.schema.addColumn("Lunas", new BooleanType(), false);
    }

    private String csv(Delimiter d, Transaction... txs) {
        return new CsvExporter(d).render(List.of(txs), fx.schema.getSchema(), fx.categories);
    }

    private Transaction expense(LocalDate d, long amount) throws Exception {
        return new Expense(d, food, amount);
    }

    // ---- exact content ----

    @Test
    void headerAndRowsAreExactlyAsExpectedOldestFirst() throws Exception {
        Transaction nasi = expense(LocalDate.of(2026, 11, 3), 25_000);
        nasi.setCustomValue(note, "nasi, ayam");
        nasi.setCustomValue(kind, "Need");
        nasi.setCustomValue(qty, 3L);
        nasi.setCustomValue(paid, true);
        Transaction gaji = new Income(LocalDate.of(2026, 11, 1), salary, 1_000_000);

        String expected = "Tanggal,Tipe,Kategori,Jumlah,Keterangan,Jenis,Qty,Tgl,Lunas\r\n"
                + "2026-11-01,Income,Gaji,1000000,,,,,\r\n"
                + "2026-11-03,Expense,Makan,25000,\"nasi, ayam\",Need,3,,true\r\n";
        assertEquals(expected, csv(Delimiter.COMMA, nasi, gaji));     // passed newest-first: still written oldest-first
    }

    @Test
    void datesAreIsoAndAmountsArePlainWholeNumbers() throws Exception {
        Transaction t = expense(LocalDate.of(2026, 1, 5), 1_234_567);
        t.setCustomValue(when, LocalDate.of(2026, 1, 4));
        t.setCustomValue(qty, 1_500L);
        String row = csv(Delimiter.COMMA, t).lines().skip(1).findFirst().orElseThrow();
        assertTrue(row.startsWith("2026-01-05,Expense,Makan,1234567,"), row);   // no thousand separators, no "Rp"
        assertTrue(row.contains(",1500,2026-01-04,"), row);                      // numbers and dates are canonical
    }

    @Test
    void anEmptyExportStillHasTheHeader() {
        assertEquals("Tanggal,Tipe,Kategori,Jumlah,Keterangan,Jenis,Qty,Tgl,Lunas\r\n", csv(Delimiter.COMMA));
    }

    @Test
    void everyLineEndsWithCrLf() throws Exception {
        String text = csv(Delimiter.COMMA, expense(LocalDate.of(2026, 11, 3), 1_000), expense(LocalDate.of(2026, 11, 4), 2_000));
        assertTrue(text.endsWith("\r\n"));
        assertEquals(3, text.split("\r\n", -1).length - 1);            // header + 2 rows, each terminated
        assertFalse(text.replace("\r\n", "").contains("\n"));
    }

    @Test
    void hiddenColumnsAreExportedToo() throws Exception {
        fx.schema.setVisible(kind.getId(), false);
        Transaction t = expense(LocalDate.of(2026, 11, 3), 1_000);
        t.setCustomValue(kind, "Want");
        String text = csv(Delimiter.COMMA, t);
        assertTrue(text.startsWith("Tanggal,Tipe,Kategori,Jumlah,Keterangan,Jenis,"));
        assertTrue(text.contains(",Want,"));
    }

    @Test
    void aRenamedCategoryAppearsUnderItsNewName() throws Exception {
        fx.categories.rename(food, "Makan Harian");
        assertTrue(csv(Delimiter.COMMA, expense(LocalDate.of(2026, 11, 3), 1_000)).contains(",Makan Harian,"));
    }

    @Test
    void aCustomColumnNamedLikeACoreColumnIsMarked() throws Exception {
        fx.schema.addColumn("Tanggal", new TextType(), false);
        fx.schema.addColumn("jumlah", new TextType(), false);
        String header = csv(Delimiter.COMMA).lines().findFirst().orElseThrow();
        assertTrue(header.endsWith(",Tanggal (kolom),jumlah (kolom)"), header);
        assertEquals(header.split(",").length, header.split(",").length);
        assertEquals(header.split(",").length, new java.util.HashSet<>(List.of(header.toLowerCase().split(","))).size(), "headers stay unique");
    }

    // ---- quoting ----

    @Test
    void fieldsWithCommasQuotesAndLineBreaksAreQuoted() throws Exception {
        Transaction t = expense(LocalDate.of(2026, 11, 3), 1_000);
        t.setCustomValue(note, "dia bilang \"halo\", lalu\npergi");
        String text = csv(Delimiter.COMMA, t);
        assertTrue(text.contains("\"dia bilang \"\"halo\"\", lalu\npergi\""), text);   // quotes doubled, line break kept inside quotes
        assertEquals(List.of(), CsvReaderForTest.parse(text).get(1).subList(0, 0));
        assertEquals("dia bilang \"halo\", lalu\npergi", CsvReaderForTest.parse(text).get(1).get(4));  // and it reads back unchanged
    }

    @Test
    void plainFieldsAreNotQuoted() throws Exception {
        Transaction t = expense(LocalDate.of(2026, 11, 3), 1_000);
        t.setCustomValue(note, "nasi goreng");
        assertTrue(csv(Delimiter.COMMA, t).contains(",nasi goreng,"));
    }

    @Test
    void theSemicolonDelimiterSeparatesWithSemicolonsAndQuotesSemicolons() throws Exception {
        Transaction t = expense(LocalDate.of(2026, 11, 3), 1_000);
        t.setCustomValue(note, "a, b; c");
        String text = csv(Delimiter.SEMICOLON, t);
        assertTrue(text.startsWith("Tanggal;Tipe;Kategori;Jumlah;Keterangan;Jenis;Qty;Tgl;Lunas\r\n"));
        assertTrue(text.contains(";\"a, b; c\";"), text);              // the comma needs no quotes here, the semicolon does
        assertEquals("a, b; c", CsvReaderForTest.parse(text, ';').get(1).get(4));
    }

    @Test
    void everyRowHasTheSameNumberOfFieldsAsTheHeader() throws Exception {
        Transaction a = expense(LocalDate.of(2026, 11, 3), 1_000);
        a.setCustomValue(note, "x,y\n\"z\"");
        Transaction b = new Income(LocalDate.of(2026, 11, 4), salary, 5_000);
        List<List<String>> rows = CsvReaderForTest.parse(csv(Delimiter.COMMA, a, b));
        assertEquals(3, rows.size());
        for (List<String> row : rows) assertEquals(9, row.size());
    }

    // ---- spreadsheet formula injection ----

    @Test
    void textThatLooksLikeAFormulaIsDefused() throws Exception {
        for (String evil : List.of("=SUM(A1)", "+62812345", "-5 off", "@cmd", "\tx")) {
            Transaction t = expense(LocalDate.of(2026, 11, 3), 1_000);
            t.setCustomValue(note, evil);
            String cell = CsvReaderForTest.parse(csv(Delimiter.COMMA, t)).get(1).get(4);
            assertEquals("'" + evil, cell, "not defused: " + evil);
        }
    }

    @Test
    void aFormulaLikeCategoryNameIsDefusedToo() throws Exception {
        UUID evil = fx.expense("=HYPERLINK(\"http://x\")");
        Transaction t = new Expense(LocalDate.of(2026, 11, 3), evil, 1_000);
        assertEquals("'=HYPERLINK(\"http://x\")", CsvReaderForTest.parse(csv(Delimiter.COMMA, t)).get(1).get(2));
    }

    @Test
    void ordinaryTextNumbersAndDatesAreLeftAlone() throws Exception {
        Transaction t = expense(LocalDate.of(2026, 11, 3), 1_000);
        t.setCustomValue(note, "kopi - susu");                          // a dash inside the text is fine
        t.setCustomValue(qty, -5L);                                     // a negative NUMBER is data, not a formula
        List<String> row = CsvReaderForTest.parse(csv(Delimiter.COMMA, t)).get(1);
        assertEquals("kopi - susu", row.get(4));
        assertEquals("-5", row.get(6));
        assertEquals("2026-11-03", row.get(0));
    }

    /** A small RFC 4180 reader, so the tests check what a real spreadsheet would read back. */
    static final class CsvReaderForTest {
        static List<List<String>> parse(String text) { return parse(text, ','); }

        static List<List<String>> parse(String text, char delimiter) {
            List<List<String>> rows = new java.util.ArrayList<>();
            List<String> row = new java.util.ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quoted) {
                    if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else if (c == '"') quoted = false;
                    else cell.append(c);
                } else if (c == '"') quoted = true;
                else if (c == delimiter) { row.add(cell.toString()); cell.setLength(0); }
                else if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new java.util.ArrayList<>(); i++;
                } else cell.append(c);
            }
            if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
            return rows;
        }
    }
}
