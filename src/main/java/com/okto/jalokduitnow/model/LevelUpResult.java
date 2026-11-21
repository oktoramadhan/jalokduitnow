package com.okto.jalokduitnow.model;

/** What happened when XP was added: the level before and after, and the new title. */
public record LevelUpResult(int fromLevel, int toLevel, String title) {
    public boolean leveledUp() {
        return toLevel > fromLevel;
    }
}
