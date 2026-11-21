package com.okto.jalokduitnow.report;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.FieldType;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.SchemaService;
import com.okto.jalokduitnow.util.Rupiah;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The shared skeleton of a report (template method): header, summary, spending by category, optional Choice
 * breakdown, optional transaction details, footer. Subclasses decide the wording, the summary lines and which
 * expenses count as "spending"; the layout helpers live here so both reports look consistent.
 * Everything is ASCII-safe plain text in Indonesian with Rupiah formatted for Locale id-ID.
 */
public abstract class AbstractReportGenerator implements ReportGenerator {
    /** At most this many transactions are listed in the details section (messages must stay sendable). */
    public static final int MAX_DETAIL_LINES = 50;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM", Rupiah.ID);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Rupiah.ID);

    protected final FinanceService finance;
    protected final CategoryService categories;
    protected final SchemaService schemaService;
    protected final Clock clock;
    protected final ReportOptions options;

    protected AbstractReportGenerator(FinanceService finance, CategoryService categories, SchemaService schemaService,
                                      Clock clock, ReportOptions options) {
        this.finance = finance;
        this.categories = categories;
        this.schemaService = schemaService;
        this.clock = clock;
        this.options = options == null ? ReportOptions.defaults() : options;
    }

    @Override
    public final String generate(YearMonth month) {
        List<Transaction> monthTx = transactionsOf(month);
        StringBuilder out = new StringBuilder();
        writeHeader(out, month);
        writeSummary(out, month, monthTx);
        if (monthTx.isEmpty()) {
            out.append("Belum ada transaksi tercatat pada bulan ini.\n\n");
        } else {
            List<Transaction> spending = spendingOf(monthTx);
            writeCategories(out, spending);
            writeChoiceBreakdown(out, spending);
            if (options.includeDetails()) writeDetails(out, monthTx);
        }
        writeFooter(out);
        return out.toString().strip();
    }

    // ---- what subclasses decide ----

    protected abstract void writeHeader(StringBuilder out, YearMonth month);

    protected abstract void writeSummary(StringBuilder out, YearMonth month, List<Transaction> monthTx);

    protected abstract void writeFooter(StringBuilder out);

    /** The expenses that count as "spending" in this report. */
    protected abstract List<Transaction> spendingOf(List<Transaction> monthTx);

    // ---- shared sections ----

    private void writeCategories(StringBuilder out, List<Transaction> spending) {
        long total = sum(spending);
        out.append("Pengeluaran per kategori:\n");
        if (spending.isEmpty()) {
            out.append("- (belum ada pengeluaran)\n\n");
            return;
        }
        Map<UUID, Long> byCategory = spending.stream().collect(Collectors.groupingBy(Transaction::getCategoryId,
                Collectors.summingLong(Transaction::getAmount)));
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed().thenComparing(e -> categories.nameOf(e.getKey())))
                .forEach(e -> out.append("- ").append(categories.nameOf(e.getKey())).append(": ")
                        .append(Rupiah.format(e.getValue())).append(" (").append(percent(e.getValue(), total)).append("%)\n"));
        out.append('\n');
    }

    /** Spending split by the options of the chosen Choice column; skipped if there is no such column. */
    private void writeChoiceBreakdown(StringBuilder out, List<Transaction> spending) {
        if (options.choiceColumnId() == null || spending.isEmpty()) return;
        ColumnDefinition column = schemaService.getSchema().find(options.choiceColumnId()).orElse(null);
        if (column == null) return;
        FieldType<?> type = column.getType();
        if (type.options().isEmpty()) return;           // not a Choice column

        Map<String, Long> sums = new LinkedHashMap<>();
        for (String option : type.options()) sums.put(option, 0L);
        long unset = 0;
        for (Transaction t : spending) {
            Object v = t.getCustomValue(column.getId());
            if (v instanceof String s && sums.containsKey(s)) sums.merge(s, t.getAmount(), Long::sum);
            else unset += t.getAmount();
        }
        long total = sum(spending);
        out.append("Rincian ").append(column.getName()).append(":\n");
        sums.forEach((option, amount) -> {
            if (amount > 0) out.append("- ").append(option).append(": ").append(Rupiah.format(amount))
                    .append(" (").append(percent(amount, total)).append("%)\n");
        });
        if (unset > 0) out.append("- Belum diisi: ").append(Rupiah.format(unset)).append(" (")
                .append(percent(unset, total)).append("%)\n");
        out.append('\n');
    }

    private void writeDetails(StringBuilder out, List<Transaction> monthTx) {
        out.append("Detail transaksi:\n");
        List<Transaction> sorted = monthTx.stream()
                .sorted(Comparator.comparing(Transaction::getDate).thenComparing(t -> t.getCreatedAt() == null ? java.time.Instant.EPOCH : t.getCreatedAt()))
                .toList();
        int shown = Math.min(MAX_DETAIL_LINES, sorted.size());
        for (int i = 0; i < shown; i++) {
            Transaction t = sorted.get(i);
            out.append("- ").append(t.getDate().format(DAY)).append(' ').append(categories.nameOf(t.getCategoryId()))
                    .append(": ").append(signed(t));
            String extra = customText(t);
            if (!extra.isEmpty()) out.append(" (").append(extra).append(')');
            out.append('\n');
        }
        if (sorted.size() > shown) out.append("(+").append(sorted.size() - shown).append(" transaksi lain tidak ditampilkan)\n");
        out.append('\n');
    }

    /** The visible custom values of a transaction as "a, b" for the details section. */
    private String customText(Transaction t) {
        List<String> parts = new ArrayList<>();
        for (ColumnDefinition col : schemaService.getSchema().visibleColumns()) {
            String shown = col.getType().displayValue(t.getCustomValue(col.getId()));
            if (!shown.isBlank()) parts.add(shown);
        }
        return String.join(", ", parts);
    }

    // ---- helpers for subclasses ----

    protected final List<Transaction> transactionsOf(YearMonth month) {
        return finance.getAll().stream().filter(t -> YearMonth.from(t.getDate()).equals(month)).toList();
    }

    protected static long sum(List<Transaction> list) {
        return list.stream().mapToLong(Transaction::getAmount).sum();
    }

    protected static List<Transaction> expensesOf(List<Transaction> list) {
        return list.stream().filter(t -> t.signedAmount() < 0).toList();
    }

    /** Rounded whole percent of a total (0 when the total is 0). */
    protected static long percent(long part, long total) {
        return total <= 0 ? 0 : Math.round(100.0 * part / total);
    }

    /** A savings category is one named "Tabungan" or using the savings icon. */
    protected boolean isSavings(Transaction t) {
        Category c = categories.find(t.getCategoryId()).orElse(null);
        return c != null && (c.getName().equalsIgnoreCase("Tabungan") || "savings".equals(c.getIconKey()));
    }

    protected static String monthLabel(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Rupiah.ID) + " " + month.getYear();
    }

    protected String today() {
        return LocalDate.now(clock).format(LONG_DATE);
    }

    private static String signed(Transaction t) {
        return (t.signedAmount() >= 0 ? "+" : "-") + Rupiah.format(t.getAmount());
    }
}
