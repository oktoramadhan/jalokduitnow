package com.okto.jalokduitnow.achievement;

import com.okto.jalokduitnow.TestClock;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.schema.TextType;
import com.okto.jalokduitnow.service.SchemaService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AchievementTest {
    private final TestClock clock = new TestClock(LocalDate.of(2026, 12, 10));   // "last month" = November 2026
    private final UUID food = UUID.randomUUID();
    private final UUID savings = UUID.randomUUID();

    private GameContext context(GameProfile profile, List<Transaction> txs, TableSchema schema,
                                List<Category> categories, List<SavingsGoal> goals) {
        return new GameContext(profile, txs, schema, categories, goals, clock);
    }

    private GameContext simple(GameProfile profile) {
        return context(profile, List.of(), new TableSchema(), List.of(), List.of());
    }

    private static Category cat(String name, Long budget) throws Exception {
        return Category.create(name, CategoryType.EXPENSE, "other", "#112233", budget);
    }

    private static GameProfile withLongestStreak(int longest, int current) {
        return GameProfile.restore(0, current, longest, 0, null, null, null, null, 0);
    }

    // ---- common ----

    @Test
    void everyAchievementHasAnIconKeyNotAnEmoji() {
        for (Achievement a : AchievementCatalog.all()) {
            assertTrue(a.getIconKey().matches("[a-z0-9_]+"), a.getId() + " icon: " + a.getIconKey());
            assertFalse(a.getName().isBlank());
            assertFalse(a.getDescription().isBlank());    // doubles as the hint while locked
        }
        assertEquals(AchievementCatalog.all().size(),
                AchievementCatalog.all().stream().map(Achievement::getId).distinct().count());
    }

    // ---- StreakAchievement ----

    @Test
    void streakAchievementsUnlockAtSevenFourteenAndThirtyDays() {
        Achievement seven = new StreakAchievement(7, "x");
        Achievement fourteen = new StreakAchievement(14, "y");
        Achievement thirty = new StreakAchievement(30, "z");

        GameContext at6 = simple(withLongestStreak(6, 6));
        assertFalse(seven.check(at6));

        GameContext at7 = simple(withLongestStreak(7, 7));
        assertTrue(seven.check(at7));
        assertFalse(fourteen.check(at7));

        GameContext at30 = simple(withLongestStreak(30, 2));    // streak since broken: the record still counts
        assertTrue(seven.check(at30) && fourteen.check(at30) && thirty.check(at30));
        assertEquals("flame", seven.getIconKey());
    }

    // ---- BudgetAchievement ----

    @Test
    void budgetAchievementNeedsABudgetedMonthWithoutOverspending() throws Exception {
        Category food = Category.create("Makan", CategoryType.EXPENSE, "food", "#112233", 500_000L);
        // use the id the transactions point at
        UUID id = food.getId();
        Achievement a = new BudgetAchievement();
        LocalDate nov = LocalDate.of(2026, 11, 10);

        List<Transaction> within = List.of(new Expense(nov, id, 499_000));
        assertTrue(a.check(context(new GameProfile(), within, new TableSchema(), List.of(food), List.of())));

        List<Transaction> over = List.of(new Expense(nov, id, 500_001));
        assertFalse(a.check(context(new GameProfile(), over, new TableSchema(), List.of(food), List.of())));
    }

    @Test
    void budgetAchievementIgnoresMonthsWithNoActivityOrNoBudgets() throws Exception {
        Achievement a = new BudgetAchievement();
        Category budgeted = cat("Makan", 500_000L);
        Category plain = cat("Lain", null);
        LocalDate nov = LocalDate.of(2026, 11, 10);

        // budget exists but nothing was recorded last month
        assertFalse(a.check(context(new GameProfile(), List.of(), new TableSchema(), List.of(budgeted), List.of())));
        // activity but no category has a budget
        assertFalse(a.check(context(new GameProfile(), List.of(new Expense(nov, plain.getId(), 1_000)),
                new TableSchema(), List.of(plain), List.of())));
        // activity only in another month does not count
        assertFalse(a.check(context(new GameProfile(), List.of(new Expense(LocalDate.of(2026, 10, 5), budgeted.getId(), 1_000)),
                new TableSchema(), List.of(budgeted), List.of())));
    }

    // ---- SavingsAchievement ----

    @Test
    void savingsAchievementsFollowGoalProgress() throws Exception {
        SavingsGoal goal = new SavingsGoal(UUID.randomUUID(), "Laptop", 1_000_000, savings);
        Achievement half = new SavingsAchievement(50, "Setengah Jalan", "star");
        Achievement full = new SavingsAchievement(100, "Target Tercapai", "trophy");
        LocalDate d = LocalDate.of(2026, 12, 1);

        GameContext none = context(new GameProfile(), List.of(), new TableSchema(), List.of(), List.of(goal));
        assertFalse(half.check(none));

        List<Transaction> fifty = List.of(new Expense(d, savings, 500_000));
        GameContext c50 = context(new GameProfile(), fifty, new TableSchema(), List.of(), List.of(goal));
        assertTrue(half.check(c50));
        assertFalse(full.check(c50));

        List<Transaction> all = List.of(new Expense(d, savings, 1_000_000));
        assertTrue(full.check(context(new GameProfile(), all, new TableSchema(), List.of(), List.of(goal))));
        assertFalse(half.check(simple(new GameProfile())));            // no goals at all
    }

    // ---- ColumnBuilderAchievement ----

    @Test
    void theFirstColumnTheUserBuildsUnlocksTheBadge() throws Exception {
        Achievement a = new ColumnBuilderAchievement(1);
        TableSchema schema = new TableSchema();
        schema.add(new ColumnDefinition(SchemaService.DEFAULT_COLUMN_ID, "Keterangan", new TextType(), false));
        // the column every schema starts with does not count
        assertFalse(a.check(context(new GameProfile(), List.of(), schema, List.of(), List.of())));

        schema.add(new ColumnDefinition("kind", "Jenis", new TextType(), false));
        assertTrue(a.check(context(new GameProfile(), List.of(), schema, List.of(), List.of())));
        assertEquals("columns", a.getIconKey());
    }

    @Test
    void aHigherThresholdNeedsMoreCustomColumns() throws Exception {
        Achievement three = new ColumnBuilderAchievement(3);
        TableSchema schema = new TableSchema();
        schema.add(new ColumnDefinition("a", "A", new TextType(), false));
        schema.add(new ColumnDefinition("b", "B", new TextType(), false));
        assertFalse(three.check(context(new GameProfile(), List.of(), schema, List.of(), List.of())));
        schema.add(new ColumnDefinition("c", "C", new TextType(), false));
        assertTrue(three.check(context(new GameProfile(), List.of(), schema, List.of(), List.of())));
    }

    // ---- CategoryCustomizerAchievement ----

    @Test
    void customizerCountsOnlyCategoriesTheUserMadeAndKept() throws Exception {
        Achievement a = new CategoryCustomizerAchievement(3);
        Category d1 = cat("Makan", null);                 // built-in default names do not count
        Category d2 = cat("tabungan", null);
        Category u1 = cat("Kopi", null);
        Category u2 = cat("Parkir", null);
        Category u3 = cat("Hobi", null);
        Category fallback = cat("Tanpa Kategori", null);
        Category archived = cat("Lama", null);
        archived.setArchived(true);

        assertFalse(a.check(context(new GameProfile(), List.of(), new TableSchema(),
                List.of(d1, d2, u1, u2, fallback, archived), List.of())));          // only 2 user categories
        assertTrue(a.check(context(new GameProfile(), List.of(), new TableSchema(),
                List.of(d1, d2, u1, u2, u3), List.of())));
    }

    // ---- TransparencyAchievement ----

    @Test
    void transparencyUnlocksOnlyOnceADonorReportWasShared() {
        Achievement a = new TransparencyAchievement();
        assertEquals("share", a.getIconKey());
        assertFalse(a.check(simple(new GameProfile())));
        assertTrue(a.check(new GameContext(new GameProfile(), List.of(), new TableSchema(), List.of(), List.of(), clock, true)));
    }
}
