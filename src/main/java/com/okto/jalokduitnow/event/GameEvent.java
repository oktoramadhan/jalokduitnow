package com.okto.jalokduitnow.event;

import com.okto.jalokduitnow.achievement.Achievement;
import com.okto.jalokduitnow.model.LevelUpResult;
import com.okto.jalokduitnow.model.StreakChange;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.quest.Quest;

import java.time.LocalDate;

/**
 * Something that happened in the game. A sealed hierarchy: every kind of event is listed here,
 * so a listener can handle them exhaustively. Events are immutable records.
 */
public sealed interface GameEvent {
    /** A transaction was ADDED (never published for edit or delete). */
    record TransactionAdded(Transaction transaction) implements GameEvent {}

    record XpGained(int amount, String reason) implements GameEvent {}

    record LevelUp(LevelUpResult result) implements GameEvent {}

    record StreakChanged(StreakChange change) implements GameEvent {}

    record QuestCompleted(Quest quest) implements GameEvent {}

    record BadgeUnlocked(Achievement achievement, LocalDate date) implements GameEvent {}

    /** A donor report was handed over (opened in WhatsApp or copied). Only the first one is rewarded. */
    record DonorReportShared(String channel) implements GameEvent {}

    /** A reminder fired (not for the test notification). Used to start tracking "logging after a reminder". */
    record ReminderFired(String reminderId) implements GameEvent {}
}
