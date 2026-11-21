package com.okto.jalokduitnow.report;

import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.SchemaService;
import com.okto.jalokduitnow.util.Rupiah;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/** The monthly overview for yourself: income, expense, balance, how much was set aside, and where money went. */
public class PersonalReportGenerator extends AbstractReportGenerator {
    public PersonalReportGenerator(FinanceService finance, CategoryService categories, SchemaService schemaService,
                                   Clock clock, ReportOptions options) {
        super(finance, categories, schemaService, clock, options);
    }

    @Override public String title() { return "Laporan Pribadi"; }

    @Override
    protected void writeHeader(StringBuilder out, YearMonth month) {
        out.append("LAPORAN KEUANGAN - ").append(monthLabel(month)).append('\n');
        out.append("Dibuat pada ").append(today()).append("\n\n");
    }

    @Override
    protected void writeSummary(StringBuilder out, YearMonth month, List<Transaction> monthTx) {
        long income = sum(monthTx.stream().filter(t -> t.signedAmount() > 0).toList());
        long expense = sum(expensesOf(monthTx));
        long saved = sum(expensesOf(monthTx).stream().filter(this::isSavings).toList());
        out.append("Pemasukan: ").append(Rupiah.format(income)).append('\n');
        out.append("Pengeluaran: ").append(Rupiah.format(expense)).append('\n');
        out.append("Saldo: ").append(signedMoney(income - expense)).append('\n');
        if (saved > 0) out.append("Termasuk ditabung: ").append(Rupiah.format(saved)).append('\n');
        out.append('\n');
    }

    /** Personal spending is every expense (the same total the dashboard shows). */
    @Override
    protected List<Transaction> spendingOf(List<Transaction> monthTx) {
        return expensesOf(monthTx);
    }

    @Override
    protected void writeFooter(StringBuilder out) {
        out.append("Dibuat dengan JalokDuitNow");
    }

    static String signedMoney(long value) {
        return (value < 0 ? "-" : "") + Rupiah.format(Math.abs(value));
    }
}
