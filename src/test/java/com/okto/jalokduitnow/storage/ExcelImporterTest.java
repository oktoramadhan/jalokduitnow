package com.okto.jalokduitnow.storage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExcelImporterTest {
    private static final Path SAMPLE = Path.of("sample-data", "Income & Expense Tracker Okto.xlsx");

    @Test
    void importsPersonalTracker() throws Exception {
        assumeTrue(Files.exists(SAMPLE), "sample file is git-ignored; skip if missing");
        ImportResult r = new ExcelImporter().importFile(SAMPLE, null);
        System.out.println("imported=" + r.transactions().size() + " warnings=" + r.warnings().size()
                + " incCat=" + r.incomeCategories().size() + " expCat=" + r.expenseCategories().size());
        r.warnings().stream().limit(5).forEach(System.out::println);
        assertFalse(r.transactions().isEmpty());
    }
}
