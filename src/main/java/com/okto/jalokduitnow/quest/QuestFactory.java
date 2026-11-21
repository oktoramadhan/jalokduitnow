package com.okto.jalokduitnow.quest;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.model.SavingsGoal;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.model.XpPolicy;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.schema.FieldType;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.util.Rupiah;

import java.util.*;

/**
 * Builds a week's quests from templates and the user's own categories and columns:
 * log on several days, a few no-spend days, a budget for the biggest spending categories, a savings target
 * and a share limit on a Choice column (e.g. "Want" under 30%). Quest ids are stable per week and template.
 */
public class QuestFactory {
    private static final int MAX_QUESTS = 5;
    private static final long ROUND_TO = 1_000;

    private final XpPolicy policy;

    public QuestFactory(XpPolicy policy) {
        this.policy = policy;
    }

    public List<Quest> generate(QuestPeriod period, List<Category> categories, TableSchema schema,
                                List<Transaction> history, List<SavingsGoal> goals) {
        List<Quest> quests = new ArrayList<>();
        int reward = policy.questXp();
        String week = period.start().toString();

        quests.add(new LogDaysQuest("log-days:" + week, "Catat transaksi di 5 hari berbeda", period, reward, 5));
        quests.add(new NoSpendDaysQuest("no-spend:" + week, "Punya 2 hari tanpa pengeluaran", period, reward, 2));

        for (Category c : budgetCandidates(period, categories, history)) {
            long limit = weeklyLimit(c, period, history);
            if (limit > 0) {
                quests.add(new CategoryBudgetQuest("budget:" + week + ":" + c.getId(),
                        c.getName() + " tidak lebih dari " + Rupiah.format(limit), period, reward, c.getId(), limit));
            }
        }
        savingsQuest(period, categories, goals, reward, week).ifPresent(quests::add);
        choiceQuest(period, schema, reward, week).ifPresent(quests::add);

        return quests.size() > MAX_QUESTS ? new ArrayList<>(quests.subList(0, MAX_QUESTS)) : quests;
    }

    /** Up to two expense categories: those with a monthly budget first, then the biggest recent spenders. */
    private List<Category> budgetCandidates(QuestPeriod period, List<Category> categories, List<Transaction> history) {
        List<Category> active = categories.stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE && !c.isArchived()
                        && !c.getName().equalsIgnoreCase(CategoryService.FALLBACK_NAME)).toList();
        List<Category> chosen = new ArrayList<>(active.stream().filter(c -> c.getMonthlyBudget() != null)
                .sorted(Comparator.comparing(Category::getName)).limit(2).toList());
        if (chosen.size() < 2) {
            Map<UUID, Long> recent = recentSpend(period, history);
            active.stream().filter(c -> !chosen.contains(c) && recent.getOrDefault(c.getId(), 0L) > 0)
                    .sorted(Comparator.comparing((Category c) -> recent.get(c.getId())).reversed()
                            .thenComparing(Category::getName))
                    .limit(2 - chosen.size()).forEach(chosen::add);
        }
        return chosen;
    }

    /** Weekly limit: the monthly budget spread over the week, or 90% of the recent weekly average. */
    private long weeklyLimit(Category c, QuestPeriod period, List<Transaction> history) {
        if (c.getMonthlyBudget() != null) return roundUp(c.getMonthlyBudget() * 7 / 30);
        long weekly = recentSpend(period, history).getOrDefault(c.getId(), 0L) / 4; // 4 weeks of history
        return weekly <= 0 ? 0 : roundUp(Math.round(weekly * 0.9));
    }

    /** Expense per category over the 4 weeks before the period. */
    private Map<UUID, Long> recentSpend(QuestPeriod period, List<Transaction> history) {
        Map<UUID, Long> sums = new HashMap<>();
        var from = period.start().minusWeeks(4);
        for (Transaction t : history) {
            if (t.signedAmount() < 0 && !t.getDate().isBefore(from) && t.getDate().isBefore(period.start())) {
                sums.merge(t.getCategoryId(), t.getAmount(), Long::sum);
            }
        }
        return sums;
    }

    private Optional<Quest> savingsQuest(QuestPeriod period, List<Category> categories, List<SavingsGoal> goals,
                                         int reward, String week) {
        for (SavingsGoal g : goals) {
            long target = Math.max(ROUND_TO, roundUp(g.targetAmount() / 4)); // weekly slice: a quarter of the goal
            return Optional.of(new SavingsQuest("savings:" + week + ":" + g.id(),
                    "Sisihkan " + Rupiah.format(target) + " untuk " + g.name(), period, reward, g.categoryId(), target));
        }
        return categories.stream()
                .filter(c -> c.getName().equalsIgnoreCase("Tabungan") && c.getType() == CategoryType.EXPENSE && !c.isArchived())
                .findFirst()
                .map(c -> new SavingsQuest("savings:" + week + ":" + c.getId(),
                        "Menabung " + Rupiah.format(50_000) + " minggu ini", period, reward, c.getId(), 50_000));
    }

    private Optional<Quest> choiceQuest(QuestPeriod period, TableSchema schema, int reward, String week) {
        for (ColumnDefinition col : schema.visibleColumns()) {
            FieldType<?> type = col.getType();
            if (type.options().size() < 2) continue;
            String option = type.options().stream().filter(o -> o.equalsIgnoreCase("Want")).findFirst()
                    .orElse(type.options().get(type.options().size() - 1));
            return Optional.of(new ChoiceShareQuest("share:" + week + ":" + col.getId(),
                    col.getName() + " '" + option + "' di bawah 30% pengeluaran", period, reward, col.getId(), option, 30));
        }
        return Optional.empty();
    }

    private static long roundUp(long value) {
        return ((value + ROUND_TO - 1) / ROUND_TO) * ROUND_TO;
    }
}
