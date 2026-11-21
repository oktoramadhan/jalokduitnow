package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.service.CategoryService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns transactions into CSV text for spreadsheets: Tanggal, Tipe, Kategori, Jumlah, then every custom column.
 * <ul>
 *   <li>RFC 4180 quoting (fields with the delimiter, quotes or line breaks are quoted; quotes are doubled), CRLF lines.</li>
 *   <li>Dates are ISO (2026-11-21) and amounts are plain whole numbers, so any spreadsheet reads them correctly.</li>
 *   <li>Text that starts with = + - @ (or a tab/CR) gets a leading apostrophe, so a spreadsheet can never run it as a
 *       formula ("CSV injection"). Numbers and dates are not touched.</li>
 *   <li>Every custom column is exported, hidden ones too: an export should not silently leave data out.</li>
 * </ul>
 * Pure text building (no files, no threads): call it on the FX thread, then write the result in the background.
 */
public class CsvExporter {
    /** Which character separates fields. Excel with Indonesian regional settings expects a semicolon. */
    public enum Delimiter {
        COMMA(',', "Koma (,)"), SEMICOLON(';', "Titik koma (;)");

        private final char symbol;
        private final String label;

        Delimiter(char symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }

        public char symbol() { return symbol; }
        public String label() { return label; }
    }

    private static final List<String> CORE_HEADERS = List.of("Tanggal", "Tipe", "Kategori", "Jumlah");
    private static final String LINE_END = "\r\n";

    private final Delimiter delimiter;

    public CsvExporter(Delimiter delimiter) {
        this.delimiter = delimiter;
    }

    /** All rows, oldest first. An empty list still gives the header row. */
    public String render(List<Transaction> transactions, TableSchema schema, CategoryService categories) {
        List<ColumnDefinition> columns = schema.getColumns();
        StringBuilder out = new StringBuilder();
        appendRow(out, headers(columns));

        List<Transaction> sorted = transactions.stream()
                .sorted(Comparator.comparing(Transaction::getDate)
                        .thenComparing(t -> t.getCreatedAt() == null ? Instant.EPOCH : t.getCreatedAt()))
                .toList();
        for (Transaction t : sorted) {
            List<String> row = new ArrayList<>();
            row.add(t.getDate().toString());
            row.add(t.typeLabel());
            row.add(neutralize(categories.nameOf(t.getCategoryId())));
            row.add(Long.toString(t.getAmount()));
            for (ColumnDefinition col : columns) row.add(customCell(col, t.getCustomValue(col.getId())));
            appendRow(out, row);
        }
        return out.toString();
    }

    /** Core headers first, then the custom column names (a custom name equal to a core header is marked). */
    List<String> headers(List<ColumnDefinition> columns) {
        List<String> headers = new ArrayList<>(CORE_HEADERS);
        Set<String> used = new HashSet<>();
        CORE_HEADERS.forEach(h -> used.add(h.toLowerCase(Locale.ROOT)));
        for (ColumnDefinition col : columns) {
            String name = col.getName();
            if (!used.add(name.toLowerCase(Locale.ROOT))) name = name + " (kolom)";
            used.add(name.toLowerCase(Locale.ROOT));
            headers.add(name);
        }
        return headers;
    }

    private String customCell(ColumnDefinition col, Object value) {
        if (value == null) return "";
        try {
            String text = col.getType().formatValue(value);      // canonical: ISO date, plain number, true/false
            return value instanceof String ? neutralize(text) : text;
        } catch (InvalidColumnException e) {
            return neutralize(String.valueOf(value));            // a value that no longer fits is still exported
        }
    }

    /** Defuses text a spreadsheet could treat as a formula. */
    static String neutralize(String text) {
        if (text.isEmpty()) return text;
        char first = text.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + text : text;
    }

    private void appendRow(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) out.append(delimiter.symbol());
            out.append(quote(cells.get(i)));
        }
        out.append(LINE_END);
    }

    /** Quotes a field only when it has to be. */
    String quote(String field) {
        boolean needs = field.indexOf(delimiter.symbol()) >= 0 || field.indexOf('"') >= 0
                || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0;
        return needs ? '"' + field.replace("\"", "\"\"") + '"' : field;
    }
}
