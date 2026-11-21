package com.okto.jalokduitnow.reminder;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.Recipient;
import com.okto.jalokduitnow.model.Transaction;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A read-only snapshot of what a reminder may look at: transactions, the streak, budgets (on the categories),
 * recipients and the donor categories. It is built fresh for every check on the FX thread, copies its lists, and
 * offers only questions (no changes), so a reminder cannot alter the app's data.
 *
 * @param today          the date the snapshot was taken for (from the clock), so messages talk about the same day the check did
 * @param lastActiveDate the last day an entry was created, according to the game profile (may be null)
 */
public record ReminderContext(LocalDate today, List<Transaction> transactions, int streak, int freezeTokens, LocalDate lastActiveDate,
                              List<Category> categories, List<Recipient> recipients, Set<UUID> donorCategoryIds) {

    public ReminderContext {
        transactions = List.copyOf(transactions);
        categories = List.copyOf(categories);
        recipients = List.copyOf(recipients);
        donorCategoryIds = Set.copyOf(donorCategoryIds);
    }

    /** Was an entry created on this day? (An entry that was later deleted still counted for the streak.) */
    public boolean hasEntryOn(LocalDate day, ZoneId zone) {
        if (day.equals(lastActiveDate)) return true;
        return transactions.stream().anyMatch(t -> t.getCreatedAt() != null
                && LocalDate.ofInstant(t.getCreatedAt(), zone).equals(day));
    }

    /** Spending in one category during a month (by the transaction's own date). */
    public long expenseIn(UUID categoryId, YearMonth month) {
        return transactions.stream()
                .filter(t -> t.signedAmount() < 0 && t.getCategoryId().equals(categoryId)
                        && YearMonth.from(t.getDate()).equals(month))
                .mapToLong(Transaction::getAmount).sum();
    }

    /** The month the snapshot belongs to. */
    public YearMonth month() {
        return YearMonth.from(today);
    }

    public Optional<Recipient> defaultRecipient() {
        return recipients.stream().filter(Recipient::isDefault).findFirst();
    }
}
