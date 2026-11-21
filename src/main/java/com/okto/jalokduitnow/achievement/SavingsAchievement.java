package com.okto.jalokduitnow.achievement;

import com.okto.jalokduitnow.model.SavingsGoal;

/** Bring any savings goal to {@code percent} percent. */
public class SavingsAchievement extends Achievement {
    private final int percent;

    public SavingsAchievement(int percent, String name, String iconKey) {
        super("savings-" + percent, name, "Capai " + percent + "% dari salah satu target tabungan", iconKey);
        assert percent > 0 && percent <= 100;
        this.percent = percent;
    }

    public int getPercent() { return percent; }

    @Override
    public boolean check(GameContext c) {
        for (SavingsGoal goal : c.goals()) {
            if (goal.progress(c.transactions()).percent() >= percent) return true;
        }
        return false;
    }
}
