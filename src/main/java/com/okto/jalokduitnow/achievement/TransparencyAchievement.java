package com.okto.jalokduitnow.achievement;

/** Be open with the people who support you: share a donor report for the first time. */
public class TransparencyAchievement extends Achievement {
    public TransparencyAchievement() {
        super("transparent", "Transparan", "Bagikan laporan donor pertamamu", "share");
    }

    @Override
    public boolean check(GameContext c) {
        return c.donorReportShared();
    }
}
