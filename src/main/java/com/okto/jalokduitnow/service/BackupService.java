package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.storage.*;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Builds the two exports from the live services: a CSV of transactions and a full JSON backup.
 * Both methods only read data and return text, so they are safe to call on the FX thread; writing the text to a
 * file is a separate step (ExportFile) that can run in the background.
 */
public class BackupService {
    private final FinanceService finance;
    private final SchemaService schemaService;
    private final CategoryService categories;
    private final GameService game;
    private final RecipientService recipients;
    private final DonorSettings donorSettings;
    private final Clock clock;

    public BackupService(FinanceService finance, SchemaService schemaService, CategoryService categories,
                         GameService game, RecipientService recipients, DonorSettings donorSettings, Clock clock) {
        this.finance = finance;
        this.schemaService = schemaService;
        this.categories = categories;
        this.game = game;
        this.recipients = recipients;
        this.donorSettings = donorSettings;
        this.clock = clock;
    }

    /** Transactions of one month, or every transaction when {@code month} is null. */
    public List<Transaction> transactionsFor(YearMonth month) {
        return finance.getAll().stream().filter(t -> month == null || YearMonth.from(t.getDate()).equals(month)).toList();
    }

    public String transactionsCsv(YearMonth monthOrNull, CsvExporter.Delimiter delimiter) {
        return new CsvExporter(delimiter).render(transactionsFor(monthOrNull), schemaService.getSchema(), categories);
    }

    /** The backup as a data object (also what the tests read back). */
    public BackupDto snapshot(boolean includeDonors) {
        var schema = schemaService.getSchema();
        List<UUID> donorCategories = includeDonors ? List.copyOf(donorSettings.donorCategoryIds()) : List.of();
        return new BackupDto(BackupDto.APP_NAME, BackupDto.CURRENT_VERSION, Instant.now(clock),
                schema.getColumns().stream().map(ColumnDto::from).toList(),
                categories.getAll().stream().map(CategoryDto::from).toList(),
                finance.getAll().stream().map(t -> TransactionDto.from(t, schema)).toList(),
                game.exportState(),
                includeDonors ? recipients.getAll().stream().map(RecipientDto::from).toList() : List.of(),
                donorCategories);
    }

    public String backupJson(boolean includeDonors) throws StorageException {
        return toJson(snapshot(includeDonors));
    }

    public static String toJson(BackupDto backup) throws StorageException {
        try {
            return JsonSupport.newMapper().writeValueAsString(backup);
        } catch (IOException e) {
            throw new StorageException("Gagal membuat cadangan", e);
        }
    }

    /** Reads a backup file's text; used to check a backup and as the base of a future restore. */
    public static BackupDto parse(String json) throws StorageException {
        try {
            BackupDto backup = JsonSupport.newMapper().readValue(json, BackupDto.class);
            if (!BackupDto.APP_NAME.equals(backup.app())) throw new StorageException("Bukan file cadangan JalokDuitNow", null);
            if (backup.formatVersion() > BackupDto.CURRENT_VERSION) {
                throw new StorageException("Cadangan ini dibuat oleh versi aplikasi yang lebih baru", null);
            }
            return backup;
        } catch (IOException e) {
            throw new StorageException("File cadangan tidak bisa dibaca", e);
        }
    }
}
