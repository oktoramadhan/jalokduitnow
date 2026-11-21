package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.model.SavingsGoal;
import com.okto.jalokduitnow.quest.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the game saves in game.json: the profile, this week's quest definitions, which quests were
 * already rewarded, unlocked badges with their dates, and savings goals.
 * Quest PROGRESS is never saved: it is recomputed from the transactions.
 */
public record GameStateDto(ProfileDto profile, List<QuestDto> quests, List<String> rewardedQuests,
                           Map<String, LocalDate> badges, List<GoalDto> goals) {

    public record ProfileDto(long xp, int streak, int longestStreak, int freezeTokens, LocalDate lastActiveDate,
                             LocalDate settledThrough, LocalDate lastTokenWeek, LocalDate entryXpDate, int entryXpToday) {

        public static ProfileDto from(GameProfile p) {
            return new ProfileDto(p.getXp(), p.getStreak(), p.getLongestStreak(), p.getFreezeTokens(),
                    p.getLastActiveDate(), p.getSettledThrough(), p.getLastTokenWeek(), p.getEntryXpDate(), p.getEntryXpToday());
        }

        public GameProfile toProfile() {
            return GameProfile.restore(xp, streak, longestStreak, freezeTokens, lastActiveDate, settledThrough,
                    lastTokenWeek, entryXpDate, entryXpToday);
        }
    }

    /** A quest definition in flat form: {@code target} holds the limit, goal amount, day count or percent. */
    public record QuestDto(String type, String id, String title, LocalDate start, LocalDate end, int rewardXp,
                           UUID categoryId, String columnId, String option, long target) {

        public static QuestDto from(Quest q) {
            QuestPeriod period = q.getPeriod();
            return switch (q) {
                case CategoryBudgetQuest b -> new QuestDto(q.type(), q.getId(), q.getTitle(), period.start(), period.end(),
                        q.getRewardXp(), b.getCategoryId(), null, null, b.getLimit());
                case NoSpendDaysQuest n -> new QuestDto(q.type(), q.getId(), q.getTitle(), period.start(), period.end(),
                        q.getRewardXp(), null, null, null, n.getTargetDays());
                case LogDaysQuest l -> new QuestDto(q.type(), q.getId(), q.getTitle(), period.start(), period.end(),
                        q.getRewardXp(), null, null, null, l.getTargetDays());
                case SavingsQuest s -> new QuestDto(q.type(), q.getId(), q.getTitle(), period.start(), period.end(),
                        q.getRewardXp(), s.getCategoryId(), null, null, s.getTargetAmount());
                case ChoiceShareQuest c -> new QuestDto(q.type(), q.getId(), q.getTitle(), period.start(), period.end(),
                        q.getRewardXp(), null, c.getColumnId(), c.getOption(), c.getMaxSharePercent());
                default -> throw new IllegalArgumentException("Jenis quest tidak dikenal: " + q.getClass());
            };
        }

        public Quest toQuest() {
            QuestPeriod period = new QuestPeriod(start, end);
            return switch (type) {
                case "CATEGORY_BUDGET" -> new CategoryBudgetQuest(id, title, period, rewardXp, categoryId, target);
                case "NO_SPEND_DAYS" -> new NoSpendDaysQuest(id, title, period, rewardXp, (int) target);
                case "LOG_DAYS" -> new LogDaysQuest(id, title, period, rewardXp, (int) target);
                case "SAVINGS" -> new SavingsQuest(id, title, period, rewardXp, categoryId, target);
                case "CHOICE_SHARE" -> new ChoiceShareQuest(id, title, period, rewardXp, columnId, option, (int) target);
                default -> throw new IllegalArgumentException("Jenis quest tidak dikenal: " + type);
            };
        }
    }

    public record GoalDto(UUID id, String name, long targetAmount, UUID categoryId) {
        public static GoalDto from(SavingsGoal g) {
            return new GoalDto(g.id(), g.name(), g.targetAmount(), g.categoryId());
        }

        public SavingsGoal toGoal() {
            return new SavingsGoal(id, name, targetAmount, categoryId);
        }
    }
}
