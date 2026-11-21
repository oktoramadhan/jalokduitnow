package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.model.MonthlyScore.Grade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonthlyScoreTest {
    @Test
    void gradesFollowTheBalanceToIncomeRatio() {
        assertEquals(Grade.A, MonthlyScore.of(1_000, 700).grade());   // keeps exactly 30%
        assertEquals(Grade.B, MonthlyScore.of(1_000, 701).grade());   // just under 30%
        assertEquals(Grade.B, MonthlyScore.of(1_000, 850).grade());   // exactly 15%
        assertEquals(Grade.C, MonthlyScore.of(1_000, 851).grade());
        assertEquals(Grade.C, MonthlyScore.of(1_000, 1_000).grade()); // break even
        assertEquals(Grade.D, MonthlyScore.of(1_000, 1_001).grade());
    }

    @Test
    void noIncomeOrNoActivity() {
        assertEquals(Grade.NONE, MonthlyScore.of(0, 0).grade());
        assertEquals(Grade.D, MonthlyScore.of(0, 50_000).grade());
        assertEquals(0.0, MonthlyScore.of(0, 50_000).ratio());
    }

    @Test
    void ratioIsExposed() {
        assertEquals(0.25, MonthlyScore.of(1_000, 750).ratio(), 1e-9);
    }
}
