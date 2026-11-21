package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.model.Transaction;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Spend at most {@code limit} Rupiah in one category during the week. Completes when the week ends within the limit. */
public class CategoryBudgetQuest extends Quest {
    private final UUID categoryId;
    private final long limit;

    public CategoryBudgetQuest(String id, String title, QuestPeriod period, int rewardXp, UUID categoryId, long limit) {
        super(id, title, period, rewardXp);
        assert limit > 0;
        this.categoryId = categoryId;
        this.limit = limit;
    }

    public UUID getCategoryId() { return categoryId; }
    public long getLimit() { return limit; }

    @Override public String type() { return "CATEGORY_BUDGET"; }

    @Override
    public QuestProgress evaluate(List<Transaction> transactions, Clock clock) {
        long spent = inPeriod(transactions).stream()
                .filter(t -> isExpense(t) && t.getCategoryId().equals(categoryId))
                .mapToLong(Transaction::getAmount).sum();
        QuestStatus status;
        if (spent > limit) status = QuestStatus.FAILED;                       // spending never goes down: over is over
        else if (getPeriod().isOver(today(clock))) status = QuestStatus.COMPLETED;
        else status = QuestStatus.IN_PROGRESS;
        return new QuestProgress(spent, limit, status);
    }
}
