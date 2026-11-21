package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.model.Expense;
import com.okto.jalokduitnow.model.Income;
import com.okto.jalokduitnow.model.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Week under test: Monday 2026-11-16 .. Sunday 2026-11-22. */
class QuestTest {
    private static final LocalDate MON = LocalDate.of(2026, 11, 16);
    private static final QuestPeriod WEEK = QuestPeriod.weekOf(MON.plusDays(3));
    private final UUID food = UUID.randomUUID();
    private final UUID savings = UUID.randomUUID();
    private final UUID salary = UUID.randomUUID();
    private final TestClock clock = new TestClock(MON.plusDays(2));   // Wednesday

    private static Transaction expense(LocalDate d, UUID cat, long amount) throws Exception {
        return new Expense(d, cat, amount);
    }

    private static Transaction expense(LocalDate d, UUID cat, long amount, String columnId, Object value,
                                       com.okto.jalokduitnow.schema.ColumnDefinition col) throws Exception {
        Transaction t = new Expense(d, cat, amount);
        t.setCustomValue(col, value);
        return t;
    }

    // ---- period ----

    @Test
    void weekRunsMondayToSunday() {
        assertEquals(MON, WEEK.start());
        assertEquals(MON.plusDays(6), WEEK.end());
        assertEquals(7, WEEK.days());
        assertTrue(WEEK.contains(MON));
        assertTrue(WEEK.contains(MON.plusDays(6)));
        assertFalse(WEEK.contains(MON.minusDays(1)));
        assertFalse(WEEK.isOver(MON.plusDays(6)));
        assertTrue(WEEK.isOver(MON.plusDays(7)));
        assertEquals(WEEK, QuestPeriod.weekOf(MON.plusDays(6)));   // Sunday belongs to the same week
    }

    // ---- CategoryBudgetQuest ----

    @Test
    void budgetQuestStaysInProgressUnderTheLimitUntilTheWeekEnds() throws Exception {
        var quest = new CategoryBudgetQuest("b", "t", WEEK, 50, food, 100_000);
        List<Transaction> txs = List.of(expense(MON, food, 30_000), expense(MON.plusDays(1), food, 20_000));

        QuestProgress p = quest.evaluate(txs, clock);
        assertEquals(50_000, p.current());
        assertEquals(100_000, p.target());
        assertEquals(QuestStatus.IN_PROGRESS, p.status());
        assertEquals(0.5, p.fraction(), 1e-9);

        clock.set(WEEK.end().plusDays(1));                       // week over, still within the limit
        assertEquals(QuestStatus.COMPLETED, quest.evaluate(txs, clock).status());
    }

    @Test
    void budgetQuestFailsAsSoonAsTheLimitIsExceeded() throws Exception {
        var quest = new CategoryBudgetQuest("b", "t", WEEK, 50, food, 100_000);
        QuestProgress p = quest.evaluate(List.of(expense(MON, food, 100_001)), clock);
        assertEquals(QuestStatus.FAILED, p.status());
        assertEquals(1.0, p.fraction());                          // bar is capped at full
    }

    @Test
    void budgetQuestCountsOnlyThatCategoryExpensesInThePeriod() throws Exception {
        var quest = new CategoryBudgetQuest("b", "t", WEEK, 50, food, 100_000);
        List<Transaction> txs = List.of(
                expense(MON, food, 10_000),
                expense(MON, UUID.randomUUID(), 99_000),                 // other category
                expense(MON.minusDays(1), food, 99_000),                 // last week
                expense(WEEK.end().plusDays(1), food, 99_000),           // next week
                new Income(MON, food, 99_000));                          // income is not spending
        assertEquals(10_000, quest.evaluate(txs, clock).current());
    }

    // ---- NoSpendDaysQuest ----

    @Test
    void noSpendQuestCountsFinishedDaysWithoutExpenses() throws Exception {
        var quest = new NoSpendDaysQuest("n", "t", WEEK, 50, 2);
        List<Transaction> txs = List.of(expense(MON, food, 5_000));     // Monday has spending

        // Wednesday: finished days are Mon (spent) and Tue (free) -> 1
        QuestProgress p = quest.evaluate(txs, clock);
        assertEquals(1, p.current());
        assertEquals(QuestStatus.IN_PROGRESS, p.status());

        clock.set(MON.plusDays(3));                                      // Thursday: Wed is also free -> 2
        assertEquals(QuestStatus.COMPLETED, quest.evaluate(txs, clock).status());
    }

    @Test
    void todayDoesNotCountUntilItIsOver() throws Exception {
        var quest = new NoSpendDaysQuest("n", "t", WEEK, 50, 1);
        clock.set(MON);                                                  // Monday: no finished day yet
        assertEquals(0, quest.evaluate(List.of(), clock).current());
    }

    @Test
    void noSpendQuestFailsWhenTheWeekEndsShortOfTheTarget() throws Exception {
        var quest = new NoSpendDaysQuest("n", "t", WEEK, 50, 3);
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < 7; i++) if (i != 1 && i != 3) txs.add(expense(MON.plusDays(i), food, 1_000)); // free: Tue, Thu
        clock.set(WEEK.end().plusDays(1));
        QuestProgress p = quest.evaluate(txs, clock);
        assertEquals(2, p.current());
        assertEquals(QuestStatus.FAILED, p.status());
    }

    // ---- LogDaysQuest ----

    @Test
    void logDaysCountsDistinctDays() throws Exception {
        var quest = new LogDaysQuest("l", "t", WEEK, 50, 3);
        List<Transaction> txs = List.of(expense(MON, food, 1), expense(MON, food, 2), expense(MON.plusDays(2), food, 3));
        QuestProgress p = quest.evaluate(txs, clock);
        assertEquals(2, p.current());                                    // two different days, not three entries
        assertEquals(QuestStatus.IN_PROGRESS, p.status());

        List<Transaction> more = new ArrayList<>(txs);
        more.add(new Income(MON.plusDays(4), salary, 1));                // income counts as logging too
        assertEquals(QuestStatus.COMPLETED, quest.evaluate(more, clock).status());
    }

    @Test
    void logDaysFailsAtWeekEndIfNotReached() throws Exception {
        var quest = new LogDaysQuest("l", "t", WEEK, 50, 3);
        clock.set(WEEK.end().plusDays(1));
        assertEquals(QuestStatus.FAILED, quest.evaluate(List.of(expense(MON, food, 1)), clock).status());
    }

    // ---- SavingsQuest ----

    @Test
    void savingsQuestCompletesWhenTheTargetIsReached() throws Exception {
        var quest = new SavingsQuest("s", "t", WEEK, 50, savings, 100_000);
        QuestProgress partial = quest.evaluate(List.of(expense(MON, savings, 60_000)), clock);
        assertEquals(60_000, partial.current());
        assertEquals(QuestStatus.IN_PROGRESS, partial.status());

        List<Transaction> txs = List.of(expense(MON, savings, 60_000), expense(MON.plusDays(1), savings, 40_000),
                expense(MON.plusDays(1), food, 500_000));               // other categories do not count
        assertEquals(QuestStatus.COMPLETED, quest.evaluate(txs, clock).status());
    }

    @Test
    void savingsQuestFailsAtWeekEndIfShort() throws Exception {
        var quest = new SavingsQuest("s", "t", WEEK, 50, savings, 100_000);
        clock.set(WEEK.end().plusDays(1));
        assertEquals(QuestStatus.FAILED, quest.evaluate(List.of(expense(MON, savings, 1_000)), clock).status());
    }

    // ---- ChoiceShareQuest ----

    private com.okto.jalokduitnow.schema.ColumnDefinition kindColumn() throws Exception {
        return new com.okto.jalokduitnow.schema.ColumnDefinition("kind", "Jenis",
                new com.okto.jalokduitnow.schema.ChoiceType(List.of("Need", "Want")), false);
    }

    @Test
    void choiceShareIsMeasuredAsPercentOfSpending() throws Exception {
        var col = kindColumn();
        var quest = new ChoiceShareQuest("c", "t", WEEK, 50, "kind", "Want", 30);
        List<Transaction> txs = List.of(
                expense(MON, food, 80_000, "kind", "Need", col),
                expense(MON, food, 20_000, "kind", "Want", col));
        QuestProgress p = quest.evaluate(txs, clock);
        assertEquals(20, p.current());                                   // 20% of 100k
        assertEquals(30, p.target());
        assertEquals(QuestStatus.IN_PROGRESS, p.status());               // the verdict comes at week end

        clock.set(WEEK.end().plusDays(1));
        assertEquals(QuestStatus.COMPLETED, quest.evaluate(txs, clock).status());
    }

    @Test
    void choiceShareFailsWhenTooHighAtWeekEnd() throws Exception {
        var col = kindColumn();
        var quest = new ChoiceShareQuest("c", "t", WEEK, 50, "kind", "Want", 30);
        List<Transaction> txs = List.of(
                expense(MON, food, 50_000, "kind", "Need", col),
                expense(MON, food, 50_000, "kind", "Want", col));
        clock.set(WEEK.end().plusDays(1));
        QuestProgress p = quest.evaluate(txs, clock);
        assertEquals(50, p.current());
        assertEquals(QuestStatus.FAILED, p.status());
    }

    @Test
    void choiceShareWithNoSpendingDoesNotCompleteForFree() throws Exception {
        var quest = new ChoiceShareQuest("c", "t", WEEK, 50, "kind", "Want", 30);
        clock.set(WEEK.end().plusDays(1));
        assertEquals(QuestStatus.FAILED, quest.evaluate(List.of(), clock).status());
    }

    // ---- progress is always recomputed ----

    @Test
    void progressIsAPureFunctionOfTheTransactions() throws Exception {
        var quest = new CategoryBudgetQuest("b", "t", WEEK, 50, food, 100_000);
        Transaction a = expense(MON, food, 30_000);
        Transaction b = expense(MON.plusDays(1), food, 20_000);

        assertEquals(quest.evaluate(List.of(a, b), clock), quest.evaluate(List.of(a, b), clock)); // same input, same output

        b.setAmount(70_000);                                              // edit: reflected immediately
        assertEquals(100_000, quest.evaluate(List.of(a, b), clock).current());
        assertEquals(30_000, quest.evaluate(List.of(a), clock).current()); // delete: reflected immediately

        // editing a field the quest does not look at changes nothing
        QuestProgress before = quest.evaluate(List.of(a), clock);
        a.setDate(a.getDate());
        assertEquals(before, quest.evaluate(List.of(a), clock));
    }
}
