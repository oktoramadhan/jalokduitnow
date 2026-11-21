package com.okto.jalokduitnow.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XpPolicyTest {
    private final XpPolicy policy = new XpPolicy();

    @Test
    void defaultsMatchTheRules() {
        assertEquals(10, policy.firstEntryOfDayXp());
        assertEquals(3, policy.completeEntryXp());
        assertEquals(50, policy.questXp());
        assertEquals(100, policy.badgeXp());
        assertEquals(30, policy.entryDailyCap());
    }

    @Test
    void entryXpIsFiveUntilTheDailyCapOfThirty() {
        assertEquals(5, policy.entryXp(0));
        assertEquals(5, policy.entryXp(25));   // 25 + 5 = exactly the cap
        assertEquals(0, policy.entryXp(30));
        assertEquals(0, policy.entryXp(45));
    }

    @Test
    void capAppliesPartially() {
        XpPolicy p = new XpPolicy(8, 30, 10, 3, 50, 100);
        assertEquals(8, p.entryXp(16));
        assertEquals(6, p.entryXp(24));        // only 6 left under the cap
    }

    @Test
    void policyIsTweakable() {
        XpPolicy p = new XpPolicy(1, 2, 3, 4, 5, 6);
        assertEquals(1, p.entryXp(0));
        assertEquals(0, p.entryXp(2));
        assertEquals(5, p.questXp());
        assertEquals(6, p.badgeXp());
    }
}
