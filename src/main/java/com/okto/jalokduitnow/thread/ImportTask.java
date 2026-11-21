package com.okto.jalokduitnow.thread;

import com.okto.jalokduitnow.storage.ExcelImporter;
import com.okto.jalokduitnow.storage.ImportResult;
import javafx.concurrent.Task;

import java.nio.file.Path;

/** Runs the Excel import off the FX thread; progress/value callbacks are delivered on the FX thread. */
public class ImportTask extends Task<ImportResult> {
    private final Path file;

    public ImportTask(Path file) {
        this.file = file;
    }

    @Override
    protected ImportResult call() throws Exception {
        updateMessage("Membaca " + file.getFileName() + "...");
        ImportResult result = new ExcelImporter().importFile(file, p -> updateProgress(p, 1.0));
        updateMessage("Selesai: " + result.transactions().size() + " transaksi");
        return result;
    }
}
