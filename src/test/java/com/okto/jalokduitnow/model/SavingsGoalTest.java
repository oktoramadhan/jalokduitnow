package com.okto.jalokduitnow.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SavingsGoalTest {
    private final UUID savings = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private Transaction tx(UUID category, long amount) throws Exception {
        return new Expense(LocalDate.of(2026, 11, 16), category, amount);
    }

    @Test
    void progressSumsOnlyTheLinkedCategory() throws Exception {
        SavingsGoal goal = new SavingsGoal(UUID.randomUUID(), "Laptop", 1_000_000, savings);
        var p = goal.progress(List.of(tx(savings, 200_000), tx(savings, 50_000), tx(other, 900_000)));
        assertEquals(250_000, p.saved());
        assertEquals(25, p.percent());
    }

    @Test
    void percentIsCappedAt100() throws Exception {
        SavingsGoal goal = new SavingsGoal(UUID.randomUUID(), "Laptop", 100_000, savings);
        assertEquals(100, goal.progress(List.of(tx(savings, 500_000))).percent());
        assertEquals(0, goal.progress(List.of()).percent());
    }

    @Test
    void validatesItsFields() {
        assertThrows(IllegalArgumentException.class, () -> new SavingsGoal(UUID.randomUUID(), " ", 1, savings));
        assertThrows(IllegalArgumentException.class, () -> new SavingsGoal(UUID.randomUUID(), "x", 0, savings));
        assertThrows(IllegalArgumentException.class, () -> new SavingsGoal(UUID.randomUUID(), "x", 1, null));
    }
}
