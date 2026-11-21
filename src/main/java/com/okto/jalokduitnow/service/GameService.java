package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.achievement.Achievement;
import com.okto.jalokduitnow.achievement.AchievementCatalog;
import com.okto.jalokduitnow.achievement.GameContext;
import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.exception.InvalidGoalException;
import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.*;
import com.okto.jalokduitnow.quest.Quest;
import com.okto.jalokduitnow.quest.QuestFactory;
import com.okto.jalokduitnow.quest.QuestPeriod;
import com.okto.jalokduitnow.quest.QuestProgress;
import com.okto.jalokduitnow.schema.ColumnDefinition;
import com.okto.jalokduitnow.storage.GameStateDto;
import com.okto.jalokduitnow.storage.GameStateDto.GoalDto;
import com.okto.jalokduitnow.storage.GameStateDto.ProfileDto;
import com.okto.jalokduitnow.storage.GameStateDto.QuestDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Consumer;

/**
 * The game rules in one place. It listens to {@code TransactionAdded} on the event bus, awards XP, updates the
 * streak, re-evaluates quests and badges, and publishes what happened (XpGained, LevelUp, StreakChanged,
 * QuestCompleted, BadgeUnlocked). XP is only ever awarded for ADDED transactions; edits and deletes change
 * nothing here (quest progress is recomputed from the transactions whenever it is asked for).
 * All time comes from the injected Clock. Not thread-safe: call it from one thread (the FX thread in the app).
 */
public class GameService {
    private final AutoSaveService<GameStateDto> autoSave;
    private final Repository<GameStateDto> repository;
    private final FinanceService finance;
    private final CategoryService categories;
    private final SchemaService schemaService;
    private final GameEventBus bus;
    private final XpPolicy policy;
    private final Clock clock;
    private final QuestFactory questFactory;
    private final List<Achievement> catalog = AchievementCatalog.all();

    private GameProfile profile = new GameProfile();
    private List<Quest> quests = new ArrayList<>();
    private final Set<String> rewardedQuests = new LinkedHashSet<>();
    private final Map<String, LocalDate> unlockedBadges = new LinkedHashMap<>();
    private final List<SavingsGoal> goals = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean donorReportShared;   // the one-time donor bonus has been paid
    private LocalDate firstReminderDay;  // the day the first reminder appeared (null: none yet)
    private boolean loaded;
    private boolean rewardsSuspended;     // true while a restore reloads data: nothing may award XP or badges

    public GameService(Repository<GameStateDto> repository, FinanceService finance, CategoryService categories,
                       SchemaService schemaService, GameEventBus bus, XpPolicy policy, Clock clock) {
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
        this.finance = finance;
        this.categories = categories;
        this.schemaService = schemaService;
        this.bus = bus;
        this.policy = policy;
        this.clock = clock;
        this.questFactory = new QuestFactory(policy);

        bus.subscribe(GameEvent.TransactionAdded.class, this::onTransactionAdded);
        bus.subscribe(GameEvent.DonorReportShared.class, this::onDonorReportShared);
        bus.subscribe(GameEvent.ReminderFired.class, this::onReminderFired);
        // badges about columns and categories change when those change
        schemaService.addListener(this::onStructureChanged);
        categories.addListener(this::onStructureChanged);
    }

    // ---- lifecycle ----

    public void addListener(Runnable listener) { listeners.add(listener); }

    public void setErrorHandler(Consumer<StorageException> handler) { autoSave.setErrorHandler(handler); }

    public void flush() { autoSave.flush(); }

    /**
     * While true, changes to columns or categories do not unlock badges or give XP. A restore switches this on while it
     * reloads the data, so restored data is taken exactly as it was saved and never "earns" anything.
     */
    public void suspendRewards(boolean suspended) {
        this.rewardsSuspended = suspended;
    }

    /** Forgets the in-memory game state, reads game.json again and tells the listeners. Awards nothing. */
    public void reload() throws StorageException {
        profile = new GameProfile();
        quests = new ArrayList<>();
        rewardedQuests.clear();
        unlockedBadges.clear();
        goals.clear();
        donorReportShared = false;
        firstReminderDay = null;
        loaded = false;
        load();
        listeners.forEach(Runnable::run);
    }

    public void load() throws StorageException {
        List<GameStateDto> saved = repository.findAll();
        if (!saved.isEmpty()) {
            GameStateDto s = saved.get(0);
            try {
                if (s.profile() != null) profile = s.profile().toProfile();
                quests = new ArrayList<>();
                if (s.quests() != null) for (QuestDto q : s.quests()) quests.add(q.toQuest());
                rewardedQuests.clear();
                if (s.rewardedQuests() != null) rewardedQuests.addAll(s.rewardedQuests());
                unlockedBadges.clear();
                if (s.badges() != null) unlockedBadges.putAll(s.badges());
                goals.clear();
                if (s.goals() != null) for (GoalDto g : s.goals()) goals.add(g.toGoal());
                donorReportShared = Boolean.TRUE.equals(s.donorReportShared());
                firstReminderDay = s.firstReminderDay();
            } catch (IllegalArgumentException e) {
                throw new StorageException("File game rusak: " + e.getMessage(), e);
            }
        } else {
            replayHistory();   // first run: build the streak from the transactions that already exist
        }
        loaded = true;
    }

    /**
     * On the very first run (no game.json yet) the streak is rebuilt from the days existing entries were created,
     * through the same rules as live play (freeze tokens included). No XP is given and nothing is announced:
     * XP is only for entries added from now on.
     */
    private void replayHistory() {
        finance.getAll().stream()
                .filter(t -> t.getCreatedAt() != null)
                .map(t -> LocalDate.ofInstant(t.getCreatedAt(), clock.getZone()))
                .distinct().sorted()
                .forEach(profile::recordActivity);
    }

    // ---- read access for the UI ----

    public GameProfile profile() { return profile; }

    public List<Quest> quests() { return List.copyOf(quests); }

    /** Fresh progress, computed from the transactions right now. */
    public QuestProgress progressOf(Quest quest) { return quest.evaluate(finance.getAll(), clock); }

    public boolean isRewarded(Quest quest) { return rewardedQuests.contains(quest.getId()); }

    public List<Achievement> achievements() { return catalog; }

    public Optional<LocalDate> unlockedOn(Achievement a) { return Optional.ofNullable(unlockedBadges.get(a.getId())); }

    public int unlockedCount() { return unlockedBadges.size(); }

    public List<SavingsGoal> goals() { return List.copyOf(goals); }

    public SavingsGoal.Progress goalProgress(SavingsGoal goal) { return goal.progress(finance.getAll()); }

    public MonthlyScore scoreFor(YearMonth month) {
        FinanceService.MonthSummary s = finance.summaryFor(month);
        return MonthlyScore.of(s.income(), s.expense());
    }

    // ---- events ----

    private void onTransactionAdded(GameEvent.TransactionAdded event) {
        Transaction t = event.transaction();
        LocalDate day = t.getCreatedAt() == null ? LocalDate.now(clock)
                : LocalDate.ofInstant(t.getCreatedAt(), clock.getZone());

        boolean firstOfDay = !profile.hasActivityOn(day);
        StreakChange streak = profile.recordActivity(day);
        if (streak.changed()) bus.publish(new GameEvent.StreakChanged(streak));

        int xp = policy.entryXp(profile.entryXpEarnedOn(day));
        profile.noteEntryXp(day, xp);
        if (firstOfDay) xp += policy.firstEntryOfDayXp();
        if (isComplete(t)) xp += policy.completeEntryXp();
        award(xp, "Transaksi baru");

        ensureQuestsForThisWeek();
        evaluateQuests();
        evaluateBadges();
        changed();
    }

    /** The first donor report that is shared pays a one-time bonus and unlocks the Transparent badge. */
    private void onDonorReportShared(GameEvent.DonorReportShared event) {
        if (donorReportShared) return;
        donorReportShared = true;
        award(policy.donorReportXp(), "Laporan donor pertama dibagikan");
        evaluateBadges();
        changed();
    }

    /** The first reminder starts the clock for the "Konsisten" badge; later ones change nothing. */
    private void onReminderFired(GameEvent.ReminderFired event) {
        if (firstReminderDay != null) return;
        firstReminderDay = LocalDate.now(clock);
        evaluateBadges();
        changed();
    }

    /** Complete = every required custom column has a value. */
    private boolean isComplete(Transaction t) {
        for (ColumnDefinition col : schemaService.getSchema().getColumns()) {
            if (col.isRequired() && t.getCustomValue(col.getId()) == null) return false;
        }
        return true;
    }

    private void onStructureChanged() {
        if (!loaded || rewardsSuspended) return;
        if (evaluateBadges()) changed();
    }

    // ---- the daily check ----

    /**
     * Called at midnight and at startup: settles the streak up to today, pays out quests of a finished week,
     * starts this week's quests if needed, and re-checks badges.
     */
    public void rollDay() {
        LocalDate today = LocalDate.now(clock);
        StreakChange streak = profile.rollTo(today);
        if (streak.changed()) bus.publish(new GameEvent.StreakChanged(streak));

        evaluateQuests();             // pays quests of the week that just ended
        ensureQuestsForThisWeek();    // then replaces them with this week's
        evaluateQuests();
        evaluateBadges();
        changed();
    }

    private void ensureQuestsForThisWeek() {
        QuestPeriod thisWeek = QuestPeriod.weekOf(LocalDate.now(clock));
        if (!quests.isEmpty() && quests.get(0).getPeriod().equals(thisWeek)) return;
        quests = new ArrayList<>(questFactory.generate(thisWeek, categories.getAll(), schemaService.getSchema(),
                finance.getAll(), goals));
    }

    private void evaluateQuests() {
        for (Quest q : quests) {
            if (rewardedQuests.contains(q.getId())) continue;
            if (q.evaluate(finance.getAll(), clock).isCompleted()) {
                rewardedQuests.add(q.getId());       // remembered forever: no double pay, and edits never take it back
                award(q.getRewardXp(), "Quest selesai");
                bus.publish(new GameEvent.QuestCompleted(q));
            }
        }
    }

    /** Unlocks every badge whose rule is true now. @return true if anything was unlocked */
    private boolean evaluateBadges() {
        GameContext context = new GameContext(profile, finance.getAll(), schemaService.getSchema(),
                categories.getAll(), List.copyOf(goals), clock, donorReportShared, firstReminderDay);
        LocalDate today = LocalDate.now(clock);
        boolean any = false;
        for (Achievement a : catalog) {
            if (unlockedBadges.containsKey(a.getId())) continue; // unlocked badges stay unlocked
            if (a.check(context)) {
                unlockedBadges.put(a.getId(), today);
                award(policy.badgeXp() + a.bonusXp(), "Badge baru");
                bus.publish(new GameEvent.BadgeUnlocked(a, today));
                any = true;
            }
        }
        return any;
    }

    private void award(int amount, String reason) {
        if (amount <= 0) return;
        LevelUpResult result = profile.addXp(amount);
        bus.publish(new GameEvent.XpGained(amount, reason));
        if (result.leveledUp()) bus.publish(new GameEvent.LevelUp(result));
    }

    // ---- savings goals ----

    public SavingsGoal addGoal(String name, long targetAmount, UUID categoryId) throws InvalidGoalException {
        SavingsGoal goal = buildGoal(UUID.randomUUID(), name, targetAmount, categoryId);
        goals.add(goal);
        evaluateBadges();
        changed();
        return goal;
    }

    public void updateGoal(UUID id, String name, long targetAmount, UUID categoryId) throws InvalidGoalException {
        SavingsGoal updated = buildGoal(id, name, targetAmount, categoryId);
        for (int i = 0; i < goals.size(); i++) {
            if (goals.get(i).id().equals(id)) {
                goals.set(i, updated);
                evaluateBadges();
                changed();
                return;
            }
        }
        throw new InvalidGoalException("Target tidak ditemukan");
    }

    public void deleteGoal(UUID id) {
        if (goals.removeIf(g -> g.id().equals(id))) changed();
    }

    private SavingsGoal buildGoal(UUID id, String name, long targetAmount, UUID categoryId) throws InvalidGoalException {
        try {
            if (categoryId == null) throw new InvalidGoalException("Pilih kategori tabungan");
            categories.require(categoryId);
            return new SavingsGoal(id, name, targetAmount, categoryId);
        } catch (InvalidCategoryException | IllegalArgumentException e) {
            throw new InvalidGoalException(e.getMessage());
        }
    }

    // ---- reset ----

    /** Starts the game over: XP, level, streak, badges and quest rewards. Savings goals and transactions are kept. */
    public void reset() {
        profile.reset();
        rewardedQuests.clear();
        unlockedBadges.clear();
        donorReportShared = false;
        firstReminderDay = null;
        quests = new ArrayList<>();
        ensureQuestsForThisWeek();
        changed();
    }

    // ---- saving ----

    private void changed() {
        autoSave.save(List.of(snapshot()));
        listeners.forEach(Runnable::run);
    }

    /** The whole game state in its saved form (used by the backup). */
    public GameStateDto exportState() {
        return snapshot();
    }

    private GameStateDto snapshot() {
        return new GameStateDto(ProfileDto.from(profile),
                quests.stream().map(QuestDto::from).toList(),
                List.copyOf(rewardedQuests),
                new LinkedHashMap<>(unlockedBadges),
                goals.stream().map(GoalDto::from).toList(),
                donorReportShared, firstReminderDay);
    }
}
