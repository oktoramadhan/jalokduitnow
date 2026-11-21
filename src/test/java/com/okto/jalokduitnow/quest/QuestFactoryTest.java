package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.ChoiceType;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.schema.TextType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestFactoryTest {
    private static final QuestPeriod WEEK = QuestPeriod.weekOf(LocalDate.of(2026, 11, 16));
    private final QuestFactory factory = new QuestFactory(new XpPolicy());

    private static Category cat(String name, CategoryType type, Long budget) throws Exception {
        return Category.create(name, type, "other", "#112233", budget);
    }

    private static <T extends Quest> List<T> ofType(List<Quest> quests, Class<T> type) {
        return quests.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void alwaysOffersLogDaysAndNoSpendQuests() {
        List<Quest> quests = factory.generate(WEEK, List.of(), new TableSchema(), List.of(), List.of());
        assertEquals(1, ofType(quests, LogDaysQuest.class).size());
        assertEquals(1, ofType(quests, NoSpendDaysQuest.class).size());
        assertEquals(50, quests.get(0).getRewardXp());                 // from the XP policy
        assertTrue(quests.stream().allMatch(q -> q.getPeriod().equals(WEEK)));
    }

    @Test
    void budgetQuestUsesTheMonthlyBudgetSpreadOverTheWeek() throws Exception {
        Category food = cat("Makan", CategoryType.EXPENSE, 300_000L);
        List<Quest> quests = factory.generate(WEEK, List.of(food), new TableSchema(), List.of(), List.of());
        CategoryBudgetQuest q = ofType(quests, CategoryBudgetQuest.class).get(0);
        assertEquals(food.getId(), q.getCategoryId());
        assertEquals(70_000, q.getLimit());                            // 300000 * 7 / 30, rounded up to 1000
        assertTrue(q.getTitle().contains("Makan"));
        assertTrue(q.getTitle().contains("Rp 70.000"));
    }

    @Test
    void withoutABudgetTheBiggestRecentSpenderGetsNinetyPercentOfItsWeeklyAverage() throws Exception {
        Category food = cat("Makan", CategoryType.EXPENSE, null);
        Category fun = cat("Hiburan", CategoryType.EXPENSE, null);
        List<Transaction> history = new ArrayList<>();
        history.add(new Expense(WEEK.start().minusDays(3), food.getId(), 400_000));  // 4 weeks: 100k per week
        history.add(new Expense(WEEK.start().minusDays(9), fun.getId(), 40_000));
        List<Quest> quests = factory.generate(WEEK, List.of(food, fun), new TableSchema(), history, List.of());

        List<CategoryBudgetQuest> budgets = ofType(quests, CategoryBudgetQuest.class);
        assertEquals(2, budgets.size());
        assertEquals(food.getId(), budgets.get(0).getCategoryId());     // the bigger spender first
        assertEquals(90_000, budgets.get(0).getLimit());                // 100k * 0.9
    }

    @Test
    void archivedAndFallbackCategoriesGetNoBudgetQuest() throws Exception {
        Category old = cat("Lama", CategoryType.EXPENSE, 100_000L);
        old.setArchived(true);
        Category none = cat("Tanpa Kategori", CategoryType.EXPENSE, 100_000L);
        assertTrue(ofType(factory.generate(WEEK, List.of(old, none), new TableSchema(), List.of(), List.of()),
                CategoryBudgetQuest.class).isEmpty());
    }

    @Test
    void savingsQuestFollowsAGoalOrTheTabunganCategory() throws Exception {
        Category tab = cat("Tabungan", CategoryType.EXPENSE, null);
        List<Quest> byName = factory.generate(WEEK, List.of(tab), new TableSchema(), List.of(), List.of());
        SavingsQuest q1 = ofType(byName, SavingsQuest.class).get(0);
        assertEquals(tab.getId(), q1.getCategoryId());
        assertEquals(50_000, q1.getTargetAmount());

        SavingsGoal goal = new SavingsGoal(UUID.randomUUID(), "Laptop", 2_000_000, tab.getId());
        List<Quest> byGoal = factory.generate(WEEK, List.of(tab), new TableSchema(), List.of(), List.of(goal));
        SavingsQuest q2 = ofType(byGoal, SavingsQuest.class).get(0);
        assertEquals(500_000, q2.getTargetAmount());                    // a quarter of the goal
        assertTrue(q2.getTitle().contains("Laptop"));
    }

    @Test
    void choiceQuestTargetsTheWantOptionOfAChoiceColumn() throws Exception {
        TableSchema schema = new TableSchema();
        schema.add(new ColumnDefinition("note", "Catatan", new TextType(), false));   // not a Choice: ignored
        schema.add(new ColumnDefinition("kind", "Jenis", new ChoiceType(List.of("Need", "Want")), false));
        List<Quest> quests = factory.generate(WEEK, List.of(), schema, List.of(), List.of());
        ChoiceShareQuest q = ofType(quests, ChoiceShareQuest.class).get(0);
        assertEquals("kind", q.getColumnId());
        assertEquals("Want", q.getOption());
        assertEquals(30, q.getMaxSharePercent());
    }

    @Test
    void neverMoreThanFiveQuestsAndIdsAreStablePerWeek() throws Exception {
        Category a = cat("A", CategoryType.EXPENSE, 100_000L);
        Category b = cat("B", CategoryType.EXPENSE, 100_000L);
        Category tab = cat("Tabungan", CategoryType.EXPENSE, null);
        TableSchema schema = new TableSchema();
        schema.add(new ColumnDefinition("kind", "Jenis", new ChoiceType(List.of("Need", "Want")), false));

        List<Quest> first = factory.generate(WEEK, List.of(a, b, tab), schema, List.of(), List.of());
        List<Quest> again = factory.generate(WEEK, List.of(a, b, tab), schema, List.of(), List.of());
        assertTrue(first.size() <= 5);
        assertEquals(first.stream().map(Quest::getId).toList(), again.stream().map(Quest::getId).toList());
        assertEquals(first.size(), first.stream().map(Quest::getId).distinct().count());

        List<Quest> nextWeek = factory.generate(QuestPeriod.weekOf(WEEK.start().plusWeeks(1)),
                List.of(a, b, tab), schema, List.of(), List.of());
        assertTrue(nextWeek.stream().noneMatch(q -> first.stream().anyMatch(f -> f.getId().equals(q.getId()))));
    }
}
