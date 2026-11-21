package com.okto.jalokduitnow.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.okto.jalokduitnow.exception.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Stores a list of T as a JSON file. Writes to a temp file first so a crash can't corrupt data. */
public class JsonRepository<T> implements Repository<T> {
    /** Default data dir: no hardcoded paths, works on macOS and Windows. */
    public static Path defaultDataDir() {
        return Path.of(System.getProperty("user.home"), ".jalokduitnow");
    }

    private final Path file;
    private final JavaType listType;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

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
            throw new StorageException("Gagal membaca data: " + file, e);
        }
    }

    @Override
    public void saveAll(List<T> items) throws StorageException {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), items);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Gagal menyimpan data: " + file, e);
        }
    }
}
