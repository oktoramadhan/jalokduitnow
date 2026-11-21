package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.model.Transaction;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Record at least one transaction on {@code targetDays} different days of the week. */
public class LogDaysQuest extends Quest {
    private final int targetDays;

    public LogDaysQuest(String id, String title, QuestPeriod period, int rewardXp, int targetDays) {
        super(id, title, period, rewardXp);
        assert targetDays > 0;
        this.targetDays = targetDays;
    }

    public int getTargetDays() { return targetDays; }

    @Override public String type() { return "LOG_DAYS"; }

    @Override
    public QuestProgress evaluate(List<Transaction> transactions, Clock clock) {
        Set<LocalDate> days = inPeriod(transactions).stream().map(Transaction::getDate).collect(Collectors.toSet());
        QuestStatus status = days.size() >= targetDays ? QuestStatus.COMPLETED
                : getPeriod().isOver(today(clock)) ? QuestStatus.FAILED : QuestStatus.IN_PROGRESS;
        return new QuestProgress(days.size(), targetDays, status);
    }
}
