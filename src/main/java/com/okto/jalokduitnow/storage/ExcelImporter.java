package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.ImportException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Reads the personal Excel tracker.
 * Sheet "Transactions": header row 5, data from row 6, columns B..F = date, type, category, amount, description.
 * Sheet "Setup": categories, income in column B, expense in column C, from row 4.
 */
public class ExcelImporter {
    static final String TRANSACTIONS_SHEET = "Transactions";
    static final String SETUP_SHEET = "Setup";
    private static final int FIRST_DATA_ROW = 5;      // 0-based index of row 6
    private static final int FIRST_SETUP_ROW = 3;     // 0-based index of row 4
    private static final int COL_B = 1;

    private final DataFormatter formatter = new DataFormatter();

    public ImportResult importFile(Path path, DoubleConsumer progress) throws ImportException {
        try (InputStream in = Files.newInputStream(path); Workbook wb = new XSSFWorkbook(in)) {
            Sheet tx = wb.getSheet(TRANSACTIONS_SHEET);
            if (tx == null) throw new ImportException("Sheet '" + TRANSACTIONS_SHEET + "' tidak ditemukan", null);

            List<Category> income = new ArrayList<>();
            List<Category> expense = new ArrayList<>();
            Sheet setup = wb.getSheet(SETUP_SHEET);
            if (setup != null) readCategories(setup, income, expense);

            List<Transaction> result = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int last = tx.getLastRowNum();
            int total = Math.max(1, last - FIRST_DATA_ROW + 1);

            for (int r = FIRST_DATA_ROW; r <= last; r++) {
                Row row = tx.getRow(r);
                if (row != null && !isBlank(row)) {
                    try {
                        result.add(parseRow(row));
                    } catch (InvalidTransactionException | IllegalArgumentException e) {
                        warnings.add("Baris " + (r + 1) + " dilewati: " + e.getMessage());
                    }
                }
                if (progress != null) progress.accept((double) (r - FIRST_DATA_ROW + 1) / total);
            }
            return new ImportResult(result, income, expense, warnings);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImportException ie) throw ie;
            throw new ImportException("Gagal membaca file Excel: " + path.getFileName(), e);
        }
    }

    private void readCategories(Sheet setup, List<Category> income, List<Category> expense) {
        for (int r = FIRST_SETUP_ROW; r <= setup.getLastRowNum(); r++) {
            Row row = setup.getRow(r);
            if (row == null) continue;
            String inc = text(row, COL_B), exp = text(row, COL_B + 1);
            if (!inc.isBlank()) income.add(new Category(inc));
            if (!exp.isBlank()) expense.add(new Category(exp));
        }
    }

    private Transaction parseRow(Row row) throws InvalidTransactionException {
        LocalDate date = parseDate(row.getCell(COL_B));
        String type = text(row, COL_B + 1);
        Category category = new Category(text(row, COL_B + 2));
        long amount = parseAmount(row.getCell(COL_B + 3));
        String description = text(row, COL_B + 4);
        return switch (type.toLowerCase()) {
            case "income" -> new Income(date, category, amount, description);
            case "expense" -> new Expense(date, category, amount, description);
            default -> throw new InvalidTransactionException("Tipe tidak dikenal: '" + type + "'");
        };
    }

    private LocalDate parseDate(Cell cell) throws InvalidTransactionException {
        if (cell == null) throw new InvalidTransactionException("Tanggal kosong");
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        try {
            return LocalDate.parse(formatter.formatCellValue(cell).trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new InvalidTransactionException("Tanggal tidak valid");
        }
    }

    /** Rupiah has no cents: round the cell value to a whole long. */
    private long parseAmount(Cell cell) throws InvalidTransactionException {
        if (cell == null) throw new InvalidTransactionException("Jumlah kosong");
        if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) {
            return Math.round(cell.getNumericCellValue());
        }
        String digits = formatter.formatCellValue(cell).replaceAll("[^0-9-]", "");
        if (digits.isEmpty()) throw new InvalidTransactionException("Jumlah tidak valid");
        return Long.parseLong(digits);
    }

    private String text(Row row, int col) {
        Cell c = row.getCell(col);
        return c == null ? "" : formatter.formatCellValue(c).trim();
    }

    private boolean isBlank(Row row) {
        for (int c = COL_B; c <= COL_B + 4; c++) if (!text(row, c).isEmpty()) return false;
        return true;
    }
}
