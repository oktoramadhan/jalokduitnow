package com.okto.jalokduitnow;

import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.SchemaService;
import com.okto.jalokduitnow.storage.CategoryDto;
import com.okto.jalokduitnow.storage.ColumnDto;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.TransactionDto;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

/** Test helper: all services wired on one data directory, loaded in the same order as the app. */
public final class Fixture {
    public final Path dir;
    private final Clock clock;
    public final FinanceService finance;
    public final SchemaService schema;
    public final CategoryService categories;

    private Fixture(Path dir, Clock clock) throws StorageException {
        this.dir = dir;
        this.clock = clock;
        finance = new FinanceService(new JsonRepository<>(dir.resolve("transactions.json"), TransactionDto.class), clock);
        schema = new SchemaService(new JsonRepository<>(dir.resolve("schema.json"), ColumnDto.class), finance);
        categories = new CategoryService(new JsonRepository<>(dir.resolve("categories.json"), CategoryDto.class), finance);
        schema.load();
        categories.load();
        finance.load(schema.getSchema(), categories);
    }

    public static Fixture in(Path dir) throws StorageException {
        return new Fixture(dir, Clock.systemDefaultZone());
    }

    /** With a controllable clock (transactions get their creation time from it). */
    public static Fixture in(Path dir, Clock clock) throws StorageException {
        return new Fixture(dir, clock);
    }

    /** Writes everything to disk and opens a second set of services on the same files. */
    public Fixture reload() throws StorageException {
        flush();
        return new Fixture(dir, clock);
    }

    public void flush() {
        finance.flush();
        schema.flush();
        categories.flush();
    }

    /** Id of the category with this name and type (created if it does not exist). */
    public UUID cat(String name, CategoryType type) {
        try {
            return categories.idFor(type, name);
        } catch (InvalidCategoryException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public UUID expense(String name) { return cat(name, CategoryType.EXPENSE); }
    public UUID income(String name) { return cat(name, CategoryType.INCOME); }
}
