package com.okto.jalokduitnow.reminder;

import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Small builders so each reminder test reads as a story. */
final class ReminderTestData {
    private ReminderTestData() {}

    static ReminderContext context(LocalDate today, List<Transaction> tx, int streak, int tokens, LocalDate lastActive,
                                   List<Category> categories, List<Recipient> recipients) {
        return new ReminderContext(today, tx, streak, tokens, lastActive, categories, recipients, Set.of());
    }

    static ReminderContext empty(LocalDate today) {
        return context(today, List.of(), 0, 0, null, List.of(), List.of());
    }

    /** An expense created (added to the app) at the given moment. */
    static Transaction createdAt(LocalDateTime when, LocalDate date, UUID category, long amount) throws Exception {
        Transaction t = new Expense(date, category, amount);
        t.setCreatedAt(when.atZone(TestClock.ZONE).toInstant());
        return t;
    }

    static Category budgeted(String name, long budget) throws Exception {
        return Category.create(name, CategoryType.EXPENSE, "food", "#112233", budget);
    }

    static Recipient donor(String name) throws Exception {
        return new Recipient(UUID.randomUUID(), name, "081234567890", "", true);
    }
}
