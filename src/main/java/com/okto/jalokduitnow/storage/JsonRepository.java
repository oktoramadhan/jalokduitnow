package com.okto.jalokduitnow.storage;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okto.jalokduitnow.exception.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores a list of T as a JSON file.
 * Safety: writes go to a temp file first, and the previous file is kept as "name.bak".
 * An unreadable file is copied to "name.corrupt-<time>.bak" before a StorageException is thrown,
 * so nothing is ever overwritten by an empty default.
 */
public class JsonRepository<T> implements Repository<T> {
    /** Default data dir: no hardcoded paths, works on macOS and Windows. */
    public static Path defaultDataDir() {
        return Path.of(System.getProperty("user.home"), ".jalokduitnow");
    }

    private final Path file;
    private final JavaType listType;
    private final ObjectMapper mapper = JsonSupport.newMapper();

    public JsonRepository(Path file, Class<T> type) {
        assert file != null && type != null;
        this.file = file;
        this.listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
    }

    @Override
    public List<T> findAll() throws StorageException {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            return mapper.readValue(file.toFile(), listType);
        } catch (IOException e) {
            throw new StorageException("File data rusak: " + file.getFileName() + ". " + backupCorrupt(), e);
        }
    }

    @Override
    public void saveAll(List<T> items) throws StorageException {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = sibling(".tmp");
            mapper.writeValue(tmp.toFile(), items);
            if (Files.exists(file)) Files.copy(file, sibling(".bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Gagal menyimpan data: " + file, e);
        }
    }

    private Path sibling(String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }

    private String backupCorrupt() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path copy = sibling(".corrupt-" + stamp + ".bak");
        try {
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            return "Salinan disimpan di " + copy.getFileName();
        } catch (IOException e) {
            return "Salinan cadangan gagal dibuat.";
        }
    }
}
