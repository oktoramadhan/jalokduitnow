package com.okto.jalokduitnow.quest;

/**
 * How far a quest is, always computed from the transactions (never stored as a counter).
 *
 * @param current the value so far (spent, days counted, percent...)
 * @param target  the value the quest is measured against (limit, goal...)
 */
public record QuestProgress(long current, long target, QuestStatus status) {
    /** 0.0 - 1.0, for a progress bar. */
    public double fraction() {
        if (target <= 0) return status == QuestStatus.COMPLETED ? 1.0 : 0.0;
        return Math.max(0.0, Math.min(1.0, (double) current / target));
    }

    public boolean isCompleted() {
        return status == QuestStatus.COMPLETED;
    }
}
