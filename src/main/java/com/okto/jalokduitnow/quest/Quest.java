package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.model.Transaction;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * A weekly challenge. Abstraction: every quest answers the same question, "how far along are you?",
 * through {@link #evaluate}; subclasses decide what is measured (polymorphism).
 * A quest only stores its definition (title, period, target). Progress is recomputed from the
 * transactions every time, so editing or deleting a transaction is always reflected and nothing can drift.
 * The reward is paid once by the game service (it remembers rewarded quest ids), never by the quest itself.
 */
public abstract class Quest {
    private final String id;
    private final String title;
    private final QuestPeriod period;
    private final int rewardXp;

    protected Quest(String id, String title, QuestPeriod period, int rewardXp) {
        assert id != null && !id.isBlank() && title != null && period != null && rewardXp >= 0;
        this.id = id;
        this.title = title;
        this.period = period;
        this.rewardXp = rewardXp;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public QuestPeriod getPeriod() { return period; }
    public int getRewardXp() { return rewardXp; }

    /** Progress right now, computed only from the given transactions and the clock's date. */
    public abstract QuestProgress evaluate(List<Transaction> transactions, Clock clock);

    /** Stable type name used in saved files, e.g. "CATEGORY_BUDGET". */
    public abstract String type();

    // ---- helpers for subclasses ----

    protected final List<Transaction> inPeriod(List<Transaction> all) {
        return all.stream().filter(t -> period.contains(t.getDate())).toList();
    }

    protected static boolean isExpense(Transaction t) {
        return t.signedAmount() < 0;
    }

    protected static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }
}
