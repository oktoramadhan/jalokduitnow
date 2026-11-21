package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.StorageException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes an export file safely: UTF-8, to a temporary file first, then moved into place. */
public final class ExportFile {
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private ExportFile() {}

    /**
     * @param withBom write a UTF-8 byte order mark first; Excel on Windows needs it to read accented text correctly
     * @return the file written
     */
    public static Path write(Path target, String text, boolean withBom) throws StorageException {
        Path absolute = target.toAbsolutePath();
        // Files.move would quietly replace an empty folder with the file: refuse instead
        if (Files.isDirectory(absolute)) throw new StorageException("Tujuan ekspor adalah folder, bukan file: " + target.getFileName(), null);
        Path tmp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try {
            Files.createDirectories(absolute.getParent());
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = body;
            if (withBom) {
                bytes = new byte[UTF8_BOM.length + body.length];
                System.arraycopy(UTF8_BOM, 0, bytes, 0, UTF8_BOM.length);
                System.arraycopy(body, 0, bytes, UTF8_BOM.length, body.length);
            }
            Files.write(tmp, bytes);
            Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
            return absolute;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // nothing more to do: the original error is what matters
            }
            throw new StorageException("Gagal menulis file ekspor: " + target.getFileName(), e);
        }
    }
}
