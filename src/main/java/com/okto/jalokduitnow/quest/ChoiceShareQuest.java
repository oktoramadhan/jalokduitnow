package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.model.Transaction;

import java.time.Clock;
import java.util.List;

/**
 * Keep the share of spending whose Choice column equals {@code option} (e.g. "Want") at or below
 * {@code maxSharePercent}. The share can still change during the week, so the verdict comes when the week ends.
 */
public class ChoiceShareQuest extends Quest {
    private final String columnId;
    private final String option;
    private final int maxSharePercent;

    public ChoiceShareQuest(String id, String title, QuestPeriod period, int rewardXp,
                            String columnId, String option, int maxSharePercent) {
        super(id, title, period, rewardXp);
        assert maxSharePercent >= 0 && maxSharePercent <= 100;
        this.columnId = columnId;
        this.option = option;
        this.maxSharePercent = maxSharePercent;
    }

    public String getColumnId() { return columnId; }
    public String getOption() { return option; }
    public int getMaxSharePercent() { return maxSharePercent; }

    @Override public String type() { return "CHOICE_SHARE"; }

    @Override
    public QuestProgress evaluate(List<Transaction> transactions, Clock clock) {
        List<Transaction> spending = inPeriod(transactions).stream().filter(Quest::isExpense).toList();
        long total = spending.stream().mapToLong(Transaction::getAmount).sum();
        long chosen = spending.stream()
                .filter(t -> option.equalsIgnoreCase(String.valueOf(t.getCustomValue(columnId))))
                .mapToLong(Transaction::getAmount).sum();
        long share = total == 0 ? 0 : Math.round(100.0 * chosen / total);

        QuestStatus status;
        if (!getPeriod().isOver(today(clock))) status = QuestStatus.IN_PROGRESS;
        else status = total > 0 && share <= maxSharePercent ? QuestStatus.COMPLETED : QuestStatus.FAILED;
        return new QuestProgress(share, maxSharePercent, status);
    }
}
