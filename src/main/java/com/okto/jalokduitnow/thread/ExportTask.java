package com.okto.jalokduitnow.thread;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.storage.ExportFile;
import javafx.concurrent.Task;

import java.nio.file.Path;

/**
 * Writes an already-built export text to a file off the FX thread. The text is built beforehand on the FX thread
 * (a snapshot), so this task touches no shared data; its success and failure callbacks run on the FX thread.
 */
public class ExportTask extends Task<Path> {
    private final Path target;
    private final String text;
    private final boolean withBom;

    public ExportTask(Path target, String text, boolean withBom) {
        this.target = target;
        this.text = text;
        this.withBom = withBom;
    }

    @Override
    protected Path call() throws StorageException {
        updateMessage("Menulis " + target.getFileName() + "...");
        return ExportFile.write(target, text, withBom);
    }
}
