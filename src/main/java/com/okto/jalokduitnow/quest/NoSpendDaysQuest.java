package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.model.Transaction;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Have at least {@code targetDays} finished days without any expense. Today does not count until it is over. */
public class NoSpendDaysQuest extends Quest {
    private final int targetDays;

    public NoSpendDaysQuest(String id, String title, QuestPeriod period, int rewardXp, int targetDays) {
        super(id, title, period, rewardXp);
        assert targetDays > 0;
        this.targetDays = targetDays;
    }

    public int getTargetDays() { return targetDays; }

    @Override public String type() { return "NO_SPEND_DAYS"; }

    @Override
    public QuestProgress evaluate(List<Transaction> transactions, Clock clock) {
        LocalDate today = today(clock);
        Set<LocalDate> spendDays = inPeriod(transactions).stream()
                .filter(Quest::isExpense).map(Transaction::getDate).collect(Collectors.toSet());
        long free = getPeriod().start().datesUntil(getPeriod().end().plusDays(1))
                .filter(d -> d.isBefore(today))            // only days that are over
                .filter(d -> !spendDays.contains(d)).count();
        QuestStatus status = free >= targetDays ? QuestStatus.COMPLETED
                : getPeriod().isOver(today) ? QuestStatus.FAILED : QuestStatus.IN_PROGRESS;
        return new QuestProgress(free, targetDays, status);
    }
}
