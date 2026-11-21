package com.okto.jalokduitnow.report;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.ChoiceType;
import com.okto.jalokduitnow.schema.TextType;
import com.okto.jalokduitnow.service.DonorSettings;
import com.okto.jalokduitnow.storage.DonorSettingsDto;
import com.okto.jalokduitnow.storage.JsonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Clock fixed at 2026-11-21. The month under test is November 2026. */
class ReportGeneratorTest {
    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @TempDir Path dir;
    TestClock clock = new TestClock(LocalDate.of(2026, 11, 21));
    Fixture fx;
    DonorSettings donor;
    UUID donation, salary, food, transport, savings;
    ColumnDefinition kind;

    @BeforeEach
    void setUp() throws Exception {
        fx = Fixture.in(dir, clock);
        donor = new DonorSettings(new JsonRepository<>(dir.resolve("donor-settings.json"), DonorSettingsDto.class));
        donor.load();
        donation = fx.income("Donatur");
        salary = fx.income("Gaji");
        food = fx.expense("Makan");
        transport = fx.expense("Transport");
        savings = fx.expense("Tabungan");
        kind = fx.schema.addColumn("Jenis", new ChoiceType(List.of("Need", "Want")), false);
    }

    private Transaction add(LocalDate date, boolean income, UUID category, long amount, Object jenis) throws Exception {
        Transaction t = income ? new Income(date, category, amount) : new Expense(date, category, amount);
        if (jenis != null) t.setCustomValue(kind, jenis);
        fx.finance.add(t);
        return t;
    }

    /** The November used by most tests: 3,000,000 from the donor and everything else as described below. */
    private void normalMonth() throws Exception {
        donor.setDonorCategory(donation, true);
        add(LocalDate.of(2026, 11, 1), true, donation, 3_000_000, null);   // donor income
        add(LocalDate.of(2026, 11, 2), true, salary, 500_000, null);       // income that is NOT from a donor
        add(LocalDate.of(2026, 11, 3), false, food, 400_000, "Need");
        add(LocalDate.of(2026, 11, 4), false, food, 200_000, "Want");
        add(LocalDate.of(2026, 11, 5), false, transport, 200_000, null);
        add(LocalDate.of(2026, 11, 6), false, savings, 200_000, null);     // saved, not spent
        add(LocalDate.of(2026, 10, 30), false, food, 999_000, "Want");     // another month: must not appear
    }

    private DonorReportGenerator donorReport(String name, ReportOptions options) {
        return new DonorReportGenerator(fx.finance, fx.categories, fx.schema, donor, name, clock, options);
    }

    private PersonalReportGenerator personalReport(ReportOptions options) {
        return new PersonalReportGenerator(fx.finance, fx.categories, fx.schema, clock, options);
    }

    // ---- donor report: a normal month ----

    @Test
    void donorReportForANormalMonthHasExactlyTheExpectedContent() throws Exception {
        normalMonth();
        String expected = """
                Halo Pak Budi,

                Berikut laporan penggunaan dana bulan November 2026.
                Dibuat pada 21 November 2026

                Dana diterima dari donor: Rp 3.000.000
                Total pengeluaran: Rp 800.000
                Ditabung: Rp 200.000
                Sisa dana: Rp 2.000.000

                Pengeluaran per kategori:
                - Makan: Rp 600.000 (75%)
                - Transport: Rp 200.000 (25%)

                Terima kasih atas dukungan dan kepercayaannya.""";
        assertEquals(expected, donorReport("Pak Budi", ReportOptions.defaults()).generate(NOV));
    }

    @Test
    void onlyDonorMarkedIncomeCountsAsReceivedAndSavingsAreNotSpending() throws Exception {
        normalMonth();
        String text = donorReport("Pak Budi", ReportOptions.defaults()).generate(NOV);
        assertTrue(text.contains("Dana diterima dari donor: Rp 3.000.000"));      // the 500,000 salary is not included
        assertTrue(text.contains("Total pengeluaran: Rp 800.000"));               // the 200,000 saved is not included
        assertTrue(text.contains("Ditabung: Rp 200.000"));
        assertFalse(text.contains("- Tabungan:"));                                  // savings are not in the spending list
        assertFalse(text.contains("999.000"));                                      // October is not in November's report
    }

    @Test
    void severalDonorCategoriesAreAddedTogether() throws Exception {
        normalMonth();
        donor.setDonorCategory(salary, true);                                       // now salary counts as donor income too
        assertTrue(donorReport("X", ReportOptions.defaults()).generate(NOV).contains("Dana diterima dari donor: Rp 3.500.000"));
    }

    @Test
    void theRemainingBalanceCanBeNegative() throws Exception {
        donor.setDonorCategory(donation, true);
        add(LocalDate.of(2026, 11, 1), true, donation, 100_000, null);
        add(LocalDate.of(2026, 11, 2), false, food, 250_000, null);
        String text = donorReport("Budi", ReportOptions.defaults()).generate(NOV);
        assertTrue(text.contains("Sisa dana: -Rp 150.000"), text);
    }

    @Test
    void theGreetingUsesTheRecipientNameOrIsPlainWithoutOne() throws Exception {
        normalMonth();
        assertTrue(donorReport("Bu Sari", ReportOptions.defaults()).generate(NOV).startsWith("Halo Bu Sari,\n"));
        assertTrue(donorReport("", ReportOptions.defaults()).generate(NOV).startsWith("Halo,\n"));
        assertTrue(donorReport(null, ReportOptions.defaults()).generate(NOV).startsWith("Halo,\n"));
        assertTrue(donorReport("  Bu Sari  ", ReportOptions.defaults()).generate(NOV).startsWith("Halo Bu Sari,\n"));
    }

    // ---- donor report: empty month, no donor income ----

    @Test
    void anEmptyMonthIsReportedGracefully() throws Exception {
        normalMonth();
        String text = donorReport("Pak Budi", ReportOptions.defaults()).generate(YearMonth.of(2026, 9));
        assertTrue(text.contains("bulan September 2026"));
        assertTrue(text.contains("Dana diterima dari donor: Rp 0"));
        assertTrue(text.contains("Total pengeluaran: Rp 0"));
        assertTrue(text.contains("Sisa dana: Rp 0"));
        assertTrue(text.contains("Belum ada transaksi tercatat pada bulan ini."));
        assertFalse(text.contains("Pengeluaran per kategori"));                     // nothing to list
        assertTrue(text.endsWith("Terima kasih atas dukungan dan kepercayaannya."));
    }

    @Test
    void aMonthWithNoDonorIncomeSaysSoAndStillShowsTheSpending() throws Exception {
        donor.setDonorCategory(donation, true);                                     // a donor category exists, but no income in it
        add(LocalDate.of(2026, 11, 3), false, food, 100_000, null);
        String text = donorReport("Budi", ReportOptions.defaults()).generate(NOV);
        assertTrue(text.contains("Dana diterima dari donor: Rp 0"));
        assertTrue(text.contains("Catatan: belum ada pemasukan donor yang tercatat pada bulan ini."));
        assertTrue(text.contains("- Makan: Rp 100.000 (100%)"));
        assertTrue(text.contains("Sisa dana: -Rp 100.000"));
    }

    @Test
    void whenNoDonorCategoryIsChosenTheReportPointsToSettings() throws Exception {
        add(LocalDate.of(2026, 11, 1), true, donation, 3_000_000, null);
        add(LocalDate.of(2026, 11, 3), false, food, 100_000, null);
        String text = donorReport("Budi", ReportOptions.defaults()).generate(NOV);
        assertTrue(text.contains("Dana diterima dari donor: Rp 0"));
        assertTrue(text.contains("Catatan: kategori pemasukan donor belum dipilih di Pengaturan."));
    }

    // ---- options: details, choice breakdown ----

    @Test
    void transactionDetailsAreOffByDefaultAndListedWhenAsked() throws Exception {
        normalMonth();
        assertFalse(donorReport("X", ReportOptions.defaults()).generate(NOV).contains("Detail transaksi"));

        String text = donorReport("X", new ReportOptions(true, null)).generate(NOV);
        assertTrue(text.contains("Detail transaksi:"));
        assertTrue(text.contains("- 01/11 Donatur: +Rp 3.000.000"));
        assertTrue(text.contains("- 03/11 Makan: -Rp 400.000 (Need)"));            // custom value of the visible column
        assertTrue(text.contains("- 05/11 Transport: -Rp 200.000\n"));
        assertTrue(text.indexOf("01/11") < text.indexOf("03/11"), "details are in date order");
        assertFalse(text.contains("30/10"));
    }

    @Test
    void detailsIncludeTheOtherCustomColumnsThatAreVisible() throws Exception {
        ColumnDefinition note = fx.schema.addColumn("Catatan", new TextType(), false);
        donor.setDonorCategory(donation, true);
        Transaction t = new Expense(LocalDate.of(2026, 11, 3), food, 50_000);
        t.setCustomValue(note, "nasi goreng");
        t.setCustomValue(kind, "Need");
        fx.finance.add(t);
        fx.schema.setVisible(note.getId(), false);                                  // hidden columns are not shared
        assertTrue(donorReport("X", new ReportOptions(true, null)).generate(NOV).contains("- 03/11 Makan: -Rp 50.000 (Need)"));
        fx.schema.setVisible(note.getId(), true);
        assertTrue(donorReport("X", new ReportOptions(true, null)).generate(NOV).contains("(Need, nasi goreng)"));
    }

    @Test
    void theDetailsListIsCappedSoTheMessageStaysSendable() throws Exception {
        for (int i = 0; i < 60; i++) add(LocalDate.of(2026, 11, 1 + i % 28), false, food, 1_000, null);
        String text = donorReport("X", new ReportOptions(true, null)).generate(NOV);
        long lines = text.lines().filter(l -> l.matches("- \\d\\d/11 Makan: -Rp 1\\.000")).count();
        assertEquals(AbstractReportGenerator.MAX_DETAIL_LINES, lines);
        assertTrue(text.contains("(+10 transaksi lain tidak ditampilkan)"));
    }

    @Test
    void aChoiceColumnBreakdownSplitsSpendingByOption() throws Exception {
        normalMonth();
        String text = donorReport("X", new ReportOptions(false, kind.getId())).generate(NOV);
        assertTrue(text.contains("Rincian Jenis:\n- Need: Rp 400.000 (50%)\n- Want: Rp 200.000 (25%)\n- Belum diisi: Rp 200.000 (25%)"), text);
        assertTrue(text.indexOf("Pengeluaran per kategori") < text.indexOf("Rincian Jenis"));
    }

    @Test
    void aBreakdownColumnThatIsMissingOrNotAChoiceIsIgnored() throws Exception {
        normalMonth();
        ColumnDefinition note = fx.schema.addColumn("Catatan", new TextType(), false);
        assertFalse(donorReport("X", new ReportOptions(false, "no-such-column")).generate(NOV).contains("Rincian"));
        assertFalse(donorReport("X", new ReportOptions(false, note.getId())).generate(NOV).contains("Rincian"));
    }

    // ---- personal report ----

    @Test
    void thePersonalReportShowsIncomeExpenseBalanceAndAllSpending() throws Exception {
        normalMonth();
        String expected = """
                LAPORAN KEUANGAN - November 2026
                Dibuat pada 21 November 2026

                Pemasukan: Rp 3.500.000
                Pengeluaran: Rp 1.000.000
                Saldo: Rp 2.500.000
                Termasuk ditabung: Rp 200.000

                Pengeluaran per kategori:
                - Makan: Rp 600.000 (60%)
                - Tabungan: Rp 200.000 (20%)
                - Transport: Rp 200.000 (20%)

                Dibuat dengan JalokDuitNow""";    // equal amounts are listed alphabetically
        assertEquals(expected, personalReport(ReportOptions.defaults()).generate(NOV));
    }

    @Test
    void thePersonalReportHandlesAnEmptyMonthAndNegativeBalance() throws Exception {
        String empty = personalReport(ReportOptions.defaults()).generate(NOV);
        assertTrue(empty.contains("Pemasukan: Rp 0"));
        assertTrue(empty.contains("Belum ada transaksi tercatat pada bulan ini."));

        add(LocalDate.of(2026, 11, 3), false, food, 75_000, null);
        assertTrue(personalReport(ReportOptions.defaults()).generate(NOV).contains("Saldo: -Rp 75.000"));
    }

    // ---- polymorphism and plain text ----

    @Test
    void bothReportsAreUsedThroughTheSameInterface() throws Exception {
        normalMonth();
        List<ReportGenerator> generators = List.of(personalReport(ReportOptions.defaults()), donorReport("Pak Budi", ReportOptions.defaults()));
        List<String> titles = new ArrayList<>();
        for (ReportGenerator g : generators) {
            String text = g.generate(NOV);
            assertFalse(text.isBlank());
            assertTrue(text.contains("November 2026"));
            titles.add(g.title());
        }
        assertEquals(List.of("Laporan Pribadi", "Laporan Donor"), titles);
    }

    @Test
    void reportsAreAsciiPlainTextWithNoEmojiOrMarkup() throws Exception {
        normalMonth();
        for (ReportGenerator g : List.of(personalReport(new ReportOptions(true, kind.getId())),
                donorReport("Pak Budi", new ReportOptions(true, kind.getId())))) {
            String text = g.generate(NOV);
            for (char c : text.toCharArray()) {
                assertTrue(c == '\n' || (c >= 32 && c < 127), "non-ASCII or control character U+" + Integer.toHexString(c) + " in:\n" + text);
            }
            assertFalse(text.contains("*") || text.contains("_") || text.contains("#"), "no markup characters");
        }
    }

    @Test
    void generatingTwiceGivesTheSameTextAndDoesNotChangeTheData() throws Exception {
        normalMonth();
        var g = donorReport("Pak Budi", new ReportOptions(true, kind.getId()));
        int before = fx.finance.getAll().size();
        assertEquals(g.generate(NOV), g.generate(NOV));
        assertEquals(before, fx.finance.getAll().size());
    }

    @Test
    void renamingACategoryShowsTheNewNameInTheReport() throws Exception {
        normalMonth();
        fx.categories.rename(food, "Makan Harian");
        assertTrue(donorReport("X", ReportOptions.defaults()).generate(NOV).contains("- Makan Harian: Rp 600.000 (75%)"));
    }
}
