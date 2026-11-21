package com.okto.jalokduitnow.dev;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SampleDataGeneratorTest {
    @Test
    void generatesValidDataThatSurvivesAReload(@TempDir Path dir) throws Exception {
        Fixture fx = Fixture.in(dir);
        LocalDate end = LocalDate.of(2025, 11, 30);
        List<Transaction> made = SampleDataGenerator.generate(fx.schema, fx.categories, fx.finance, 300, end, new Random(42));

        assertEquals(300, made.size());
        assertTrue(made.stream().allMatch(t -> !t.getDate().isBefore(end.minusMonths(3).plusDays(1)) && !t.getDate().isAfter(end)));
        assertTrue(made.stream().anyMatch(t -> t.signedAmount() > 0));
        // every transaction points at a real category of the right type
        assertTrue(made.stream().allMatch(t -> fx.categories.find(t.getCategoryId())
                .filter(c -> c.getType() == t.categoryType()).isPresent()));
        String jenisId = fx.schema.getSchema().getColumns().stream().filter(c -> c.getName().equals("Jenis")).findFirst().get().getId();
        assertTrue(made.stream().anyMatch(t -> "Need".equals(t.getCustomValue(jenisId))));

        Fixture again = fx.reload();
        assertEquals(300, again.finance.getAll().size());
        assertEquals(fx.finance.balance(), again.finance.balance());
    }

    @Test
    void generatedDatesFollowTheClockAcrossAYearBoundary(@TempDir Path dir) throws Exception {
        var clock = new com.okto.jalokduitnow.TestClock(LocalDate.of(2027, 2, 10));
        Fixture fx = Fixture.in(dir, clock);
        List<Transaction> made = SampleDataGenerator.generate(fx.schema, fx.categories, fx.finance, 300, clock, new Random(7));

        LocalDate start = clock.today().minusMonths(3).plusDays(1);
        assertTrue(made.stream().allMatch(t -> !t.getDate().isBefore(start) && !t.getDate().isAfter(clock.today())));
        assertTrue(made.stream().anyMatch(t -> t.getDate().getYear() == clock.today().getYear() - 1), "dates before New Year");
        assertTrue(made.stream().anyMatch(t -> t.getDate().getYear() == clock.today().getYear()), "dates after New Year");
    }

    @Test
    void aDifferentClockGivesDifferentYearsSoNothingIsFixed(@TempDir Path dir) throws Exception {
        var clock = new com.okto.jalokduitnow.TestClock(LocalDate.of(2031, 7, 15));
        Fixture fx = Fixture.in(dir, clock);
        List<Transaction> made = SampleDataGenerator.generate(fx.schema, fx.categories, fx.finance, 100, clock, new Random(1));
        assertTrue(made.stream().allMatch(t -> t.getDate().getYear() == 2031));
    }
}
