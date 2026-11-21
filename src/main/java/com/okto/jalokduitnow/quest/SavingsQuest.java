package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.model.Transaction;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Put at least {@code targetAmount} Rupiah into a savings category during the week. */
public class SavingsQuest extends Quest {
    private final UUID categoryId;
    private final long targetAmount;

    public SavingsQuest(String id, String title, QuestPeriod period, int rewardXp, UUID categoryId, long targetAmount) {
        super(id, title, period, rewardXp);
        assert targetAmount > 0;
        this.categoryId = categoryId;
        this.targetAmount = targetAmount;
    }

    public UUID getCategoryId() { return categoryId; }
    public long getTargetAmount() { return targetAmount; }

    @Override public String type() { return "SAVINGS"; }

    @Override
    public QuestProgress evaluate(List<Transaction> transactions, Clock clock) {
        long saved = inPeriod(transactions).stream()
                .filter(t -> t.getCategoryId().equals(categoryId))
                .mapToLong(Transaction::getAmount).sum();
        QuestStatus status = saved >= targetAmount ? QuestStatus.COMPLETED
                : getPeriod().isOver(today(clock)) ? QuestStatus.FAILED : QuestStatus.IN_PROGRESS;
        return new QuestProgress(saved, targetAmount, status);
    }
}
