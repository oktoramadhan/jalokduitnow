package com.okto.jalokduitnow.model;

/**
 * All XP numbers in one place, so the game is easy to tweak. XP is only given when a transaction is ADDED.
 * The defaults: +5 per added entry (at most 30 XP per day from this source), +10 for the first entry of the day,
 * +3 for a complete entry, +50 for a finished quest, +100 for an unlocked badge,
 * and a one-time +25 the first time a donor report is shared.
 */
public class XpPolicy {
    private final int perEntry;
    private final int entryDailyCap;
    private final int firstEntryOfDay;
    private final int completeEntry;
    private final int quest;
    private final int badge;
    private final int donorReport;

    public XpPolicy() {
        this(5, 30, 10, 3, 50, 100);
    }

    public XpPolicy(int perEntry, int entryDailyCap, int firstEntryOfDay, int completeEntry, int quest, int badge) {
        this(perEntry, entryDailyCap, firstEntryOfDay, completeEntry, quest, badge, 25);
    }

    public XpPolicy(int perEntry, int entryDailyCap, int firstEntryOfDay, int completeEntry, int quest, int badge,
                    int donorReport) {
        assert perEntry >= 0 && entryDailyCap >= 0 && firstEntryOfDay >= 0 && completeEntry >= 0 && quest >= 0
                && badge >= 0 && donorReport >= 0;
        this.perEntry = perEntry;
        this.entryDailyCap = entryDailyCap;
        this.firstEntryOfDay = firstEntryOfDay;
        this.completeEntry = completeEntry;
        this.quest = quest;
        this.badge = badge;
        this.donorReport = donorReport;
    }

    /** XP for one more added entry, given how much entry XP was already earned that day (respects the cap). */
    public int entryXp(int alreadyEarnedToday) {
        return Math.max(0, Math.min(perEntry, entryDailyCap - alreadyEarnedToday));
    }

    public int firstEntryOfDayXp() { return firstEntryOfDay; }
    public int completeEntryXp() { return completeEntry; }
    public int questXp() { return quest; }
    public int badgeXp() { return badge; }
    /** One-time bonus for the first donor report that is shared. */
    public int donorReportXp() { return donorReport; }
    public int entryDailyCap() { return entryDailyCap; }
}
