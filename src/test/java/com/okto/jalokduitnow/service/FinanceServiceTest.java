package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FinanceServiceTest {
    @Test
    void totalsAndPersistence(@TempDir Path dir) throws Exception {
        Fixture fx = Fixture.in(dir);
        FinanceService service = fx.finance;
        LocalDate d = LocalDate.of(2025, 9, 1);
        service.add(new Income(d, fx.income("Gaji"), 1_000_000L));
        Expense e = new Expense(d, fx.expense("Makan"), 300_000L);
        service.add(e);

        assertEquals(1_000_000L, service.totalIncome());
        assertEquals(300_000L, service.totalExpense());
        assertEquals(700_000L, service.balance());

        assertTrue(service.delete(e.getId()));
        Fixture reloaded = fx.reload();
        assertEquals(1, reloaded.finance.getAll().size());
        assertEquals(1_000_000L, reloaded.finance.balance());
    }

    @Test
    void monthlyAggregationsAndUpdate(@TempDir Path dir) throws Exception {
        Fixture fx = Fixture.in(dir);
        FinanceService service = fx.finance;
        UUID makan = fx.expense("Makan"), transportId = fx.expense("Transport");
        service.add(new Income(LocalDate.of(2025, 9, 1), fx.income("Gaji"), 1_000_000L));
        service.add(new Expense(LocalDate.of(2025, 9, 5), makan, 100_000L));
        service.add(new Expense(LocalDate.of(2025, 9, 6), makan, 50_000L));
        Expense transport = new Expense(LocalDate.of(2025, 9, 7), transportId, 200_000L);
        service.add(transport);
        service.add(new Expense(LocalDate.of(2025, 10, 1), makan, 10_000L));

        var sept = YearMonth.of(2025, 9);
        assertEquals(350_000L, service.summaryFor(sept).expense());
        assertEquals(650_000L, service.summaryFor(sept).balance());
        assertEquals(List.of(transportId, makan), new ArrayList<>(service.expenseByCategory(sept).keySet())); // biggest first
        assertEquals(2, service.summaryByMonth().size());

        // edit: same id, different amount, type and category
        UUID bonus = fx.income("Bonus");
        service.update(new Income(transport.getId(), transport.getDate(), bonus, 5L));
        assertEquals(150_000L, service.summaryFor(sept).expense());
        assertEquals(1, service.countWithCategory(bonus));
        assertEquals(0, service.countWithCategory(transportId));
    }

    @Test
    void reassignMovesOnlyTheGivenCategory(@TempDir Path dir) throws Exception {
        Fixture fx = Fixture.in(dir);
        UUID a = fx.expense("A"), b = fx.expense("B"), c = fx.expense("C");
        fx.finance.add(new Expense(LocalDate.of(2025, 9, 1), a, 1L));
        fx.finance.add(new Expense(LocalDate.of(2025, 9, 2), b, 2L));
        fx.finance.add(new Expense(LocalDate.of(2025, 9, 3), c, 3L));
        fx.finance.reassignCategory(a, b);
        assertEquals(0, fx.finance.countWithCategory(a));
        assertEquals(2, fx.finance.countWithCategory(b));
        assertEquals(1, fx.finance.countWithCategory(c));
    }

    @Test
    void listenersFireOnChange(@TempDir Path dir) throws Exception {
        Fixture fx = Fixture.in(dir);
        int[] calls = {0};
        fx.finance.addListener(() -> calls[0]++);
        fx.finance.add(new Income(LocalDate.of(2025, 9, 1), fx.income("Gaji"), 1L));
        assertEquals(1, calls[0]);
    }

    @Test
    void addStampsTheCreationTimeFromTheClockAndKeepsAnExistingOne(@TempDir Path dir) throws Exception {
        var clock = new com.okto.jalokduitnow.TestClock(LocalDate.of(2026, 11, 16));
        Fixture fx = Fixture.in(dir, clock);
        Expense fresh = new Expense(LocalDate.of(2026, 11, 1), fx.expense("Makan"), 1_000L);
        fx.finance.add(fresh);
        assertEquals(clock.instant(), fresh.getCreatedAt());           // the date it was ADDED, not the entry's own date

        java.time.Instant earlier = java.time.Instant.parse("2026-01-01T00:00:00Z");
        Expense imported = new Expense(LocalDate.of(2026, 1, 1), fx.expense("Makan"), 1_000L);
        imported.setCreatedAt(earlier);
        fx.finance.add(imported);
        assertEquals(earlier, imported.getCreatedAt());

        Fixture again = fx.reload();                                   // and it is saved
        assertTrue(again.finance.getAll().stream().anyMatch(t -> earlier.equals(t.getCreatedAt())));
        assertTrue(again.finance.getAll().stream().allMatch(t -> t.getCreatedAt() != null));
    }

    @Test
    void onlyAddAnnouncesATransaction(@TempDir Path dir) throws Exception {
        Fixture fx = Fixture.in(dir);
        var bus = new com.okto.jalokduitnow.event.GameEventBus();
        int[] added = {0};
        bus.subscribe(com.okto.jalokduitnow.event.GameEvent.TransactionAdded.class, e -> added[0]++);
        fx.finance.setEventBus(bus);

        Expense t = new Expense(LocalDate.of(2026, 11, 16), fx.expense("Makan"), 1_000L);
        fx.finance.add(t);
        fx.finance.update(new Expense(t.getId(), t.getDate(), t.getCategoryId(), 2_000L));
        fx.finance.delete(t.getId());
        assertEquals(1, added[0]);
    }
}
