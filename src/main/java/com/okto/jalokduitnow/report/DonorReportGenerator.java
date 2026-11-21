package com.okto.jalokduitnow.report;

import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.DonorSettings;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.SchemaService;
import com.okto.jalokduitnow.util.Rupiah;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/**
 * The report for a donor: how much was received from donors, how it was spent, what was set aside, and what is left.
 * <ul>
 *   <li>Received = income in the categories the user marked as donor income (Settings).</li>
 *   <li>Spent = all expenses EXCEPT savings; saving money is not spending it.</li>
 *   <li>Saved = expenses in the savings category.</li>
 *   <li>Remaining = received - spent - saved.</li>
 * </ul>
 * Same {@link ReportGenerator} contract as the personal report, so the UI treats them alike.
 */
public class DonorReportGenerator extends AbstractReportGenerator {
    private final String recipientName;
    private final DonorSettings donorSettings;

    /** @param recipientName who the greeting is for (may be blank) */
    public DonorReportGenerator(FinanceService finance, CategoryService categories, SchemaService schemaService,
                                DonorSettings donorSettings, String recipientName, Clock clock, ReportOptions options) {
        super(finance, categories, schemaService, clock, options);
        this.donorSettings = donorSettings;
        this.recipientName = recipientName == null ? "" : recipientName.trim();
    }

    @Override public String title() { return "Laporan Donor"; }

    @Override
    protected void writeHeader(StringBuilder out, YearMonth month) {
        out.append(recipientName.isEmpty() ? "Halo," : "Halo " + recipientName + ",").append("\n\n");
        out.append("Berikut laporan penggunaan dana bulan ").append(monthLabel(month)).append(".\n");
        out.append("Dibuat pada ").append(today()).append("\n\n");
    }

    @Override
    protected void writeSummary(StringBuilder out, YearMonth month, List<Transaction> monthTx) {
        long received = sum(monthTx.stream()
                .filter(t -> t.signedAmount() > 0 && donorSettings.isDonorCategory(t.getCategoryId())).toList());
        long spent = sum(spendingOf(monthTx));
        long saved = sum(expensesOf(monthTx).stream().filter(this::isSavings).toList());
        long remaining = received - spent - saved;

        out.append("Dana diterima dari donor: ").append(Rupiah.format(received)).append('\n');
        out.append("Total pengeluaran: ").append(Rupiah.format(spent)).append('\n');
        out.append("Ditabung: ").append(Rupiah.format(saved)).append('\n');
        out.append("Sisa dana: ").append(PersonalReportGenerator.signedMoney(remaining)).append('\n');
        if (received == 0) {
            out.append(donorSettings.donorCategoryIds().isEmpty()
                    ? "Catatan: kategori pemasukan donor belum dipilih di Pengaturan.\n"
                    : "Catatan: belum ada pemasukan donor yang tercatat pada bulan ini.\n");
        }
        out.append('\n');
    }

    /** Spending for a donor is every expense except savings. */
    @Override
    protected List<Transaction> spendingOf(List<Transaction> monthTx) {
        return expensesOf(monthTx).stream().filter(t -> !isSavings(t)).toList();
    }

    @Override
    protected void writeFooter(StringBuilder out) {
        out.append("Terima kasih atas dukungan dan kepercayaannya.");
    }
}
