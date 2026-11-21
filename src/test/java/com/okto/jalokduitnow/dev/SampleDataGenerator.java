package com.okto.jalokduitnow.dev;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import com.okto.jalokduitnow.exception.InvalidTransactionException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.*;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.SchemaService;
import com.okto.jalokduitnow.storage.CategoryDto;
import com.okto.jalokduitnow.storage.ColumnDto;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.TransactionDto;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/**
 * DEV ONLY (lives in src/test, so it is never shipped): fills a data directory with fake data.
 * Run it against a throw-away directory, never your real ~/.jalokduitnow:
 * <pre>mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.okto.jalokduitnow.dev.SampleDataGenerator -Dexec.args="/tmp/jdn-sample"</pre>
 */
public final class SampleDataGenerator {
    private SampleDataGenerator() {}

    /** Same as {@link #generate(SchemaService, CategoryService, FinanceService, int, LocalDate, Random)} for the 3 months ending today by the clock. */
    public static List<Transaction> generate(SchemaService schemaService, CategoryService categories, FinanceService finance,
                                             int count, Clock clock, Random random)
            throws InvalidColumnException, InvalidTransactionException {
        return generate(schemaService, categories, finance, count, LocalDate.now(clock), random);
    }

    /**
     * Adds a "Jenis" (Need/Want) column if missing, then replaces all transactions with fake ones:
     * about {@code count} spread over the 3 months ending at {@code end}.
     */
    public static List<Transaction> generate(SchemaService schemaService, CategoryService categories, FinanceService finance,
                                             int count, LocalDate end, Random random)
            throws InvalidColumnException, InvalidTransactionException {
        TableSchema schema = schemaService.getSchema();
        ColumnDefinition kind = schema.getColumns().stream().filter(c -> c.getName().equals("Jenis")).findFirst().orElse(null);
        if (kind == null) kind = schemaService.addColumn("Jenis", new ChoiceType(List.of("Need", "Want")), false);
        ColumnDefinition note = schema.getColumns().stream().filter(c -> c.getType() instanceof TextType).findFirst().orElse(null);

        List<Category> incomeCats = categories.active(CategoryType.INCOME);
        List<Category> expenseCats = categories.active(CategoryType.EXPENSE);
        if (incomeCats.isEmpty() || expenseCats.isEmpty()) throw new IllegalStateException("Need active Income and Expense categories");

        LocalDate start = end.minusMonths(3).plusDays(1);
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        List<Transaction> result = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDate date = start.plusDays(random.nextLong(days));
            Transaction t;
            if (random.nextInt(100) < 12) {
                Category cat = incomeCats.get(random.nextInt(incomeCats.size()));
                t = new Income(date, cat.getId(), (1 + random.nextInt(50)) * 100_000L);
            } else {
                Category cat = expenseCats.get(random.nextInt(expenseCats.size()));
                t = new Expense(date, cat.getId(), (1 + random.nextInt(150)) * 1_000L);
                if (random.nextInt(100) < 80) t.setCustomValue(kind, random.nextBoolean() ? "Need" : "Want");
            }
            if (note != null && random.nextInt(100) < 30) t.setCustomValue(note, "contoh #" + i);
            result.add(t);
        }
        finance.replaceAll(result);
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: SampleDataGenerator <output-directory>   (not your real data dir!)");
            System.exit(1);
        }
        Path dir = Path.of(args[0]);
        if (dir.toAbsolutePath().normalize().equals(JsonRepository.defaultDataDir().toAbsolutePath().normalize())) {
            System.err.println("Refusing to overwrite the real data directory.");
            System.exit(1);
        }
        FinanceService finance = new FinanceService(new JsonRepository<>(dir.resolve("transactions.json"), TransactionDto.class));
        SchemaService schema = new SchemaService(new JsonRepository<>(dir.resolve("schema.json"), ColumnDto.class), finance);
        CategoryService categories = new CategoryService(new JsonRepository<>(dir.resolve("categories.json"), CategoryDto.class), finance);
        try {
            schema.load();
            categories.load();
            finance.load(schema.getSchema(), categories);
            generate(schema, categories, finance, 300, Clock.systemDefaultZone(), new Random());
            finance.flush();
            schema.flush();
            categories.flush();
            System.out.println("Generated 300 transactions in " + dir);
        } catch (StorageException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
