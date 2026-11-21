package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.model.SavingsGoal;
import com.okto.jalokduitnow.quest.*;
import com.okto.jalokduitnow.storage.GameStateDto.GoalDto;
import com.okto.jalokduitnow.storage.GameStateDto.ProfileDto;
import com.okto.jalokduitnow.storage.GameStateDto.QuestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameStateJsonTest {
    private static final QuestPeriod WEEK = QuestPeriod.weekOf(LocalDate.of(2026, 11, 16));

    @Test
    void profileRoundTripsThroughJson(@TempDir Path dir) throws Exception {
        GameProfile p = GameProfile.restore(1234, 5, 9, 2,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 20), 15);
        var repo = new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class);
        repo.saveAll(List.of(new GameStateDto(ProfileDto.from(p), List.of(), List.of(), Map.of(), List.of())));

        GameProfile back = repo.findAll().get(0).profile().toProfile();
        assertEquals(1234, back.getXp());
        assertEquals(p.getLevel(), back.getLevel());
        assertEquals(5, back.getStreak());
        assertEquals(9, back.getLongestStreak());
        assertEquals(2, back.getFreezeTokens());
        assertEquals(LocalDate.of(2026, 11, 20), back.getLastActiveDate());
        assertEquals(LocalDate.of(2026, 11, 20), back.getSettledThrough());
        assertEquals(LocalDate.of(2026, 11, 16), back.getLastTokenWeek());
        assertEquals(15, back.entryXpEarnedOn(LocalDate.of(2026, 11, 20)));
    }

    @Test
    void everyQuestTypeRoundTripsWithItsParameters(@TempDir Path dir) throws Exception {
        UUID cat = UUID.randomUUID();
        List<Quest> quests = List.of(
                new CategoryBudgetQuest("b", "Budget", WEEK, 50, cat, 70_000),
                new NoSpendDaysQuest("n", "No spend", WEEK, 50, 2),
                new LogDaysQuest("l", "Log", WEEK, 50, 5),
                new SavingsQuest("s", "Save", WEEK, 50, cat, 250_000),
                new ChoiceShareQuest("c", "Share", WEEK, 50, "kind", "Want", 30));
        var repo = new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class);
        repo.saveAll(List.of(new GameStateDto(ProfileDto.from(new GameProfile()),
                quests.stream().map(QuestDto::from).toList(), List.of("b"),
                Map.of("streak-7", LocalDate.of(2026, 11, 20)), List.of())));

        GameStateDto back = repo.findAll().get(0);
        List<Quest> restored = back.quests().stream().map(QuestDto::toQuest).toList();
        assertEquals(5, restored.size());
        assertEquals(70_000, ((CategoryBudgetQuest) restored.get(0)).getLimit());
        assertEquals(cat, ((CategoryBudgetQuest) restored.get(0)).getCategoryId());
        assertEquals(2, ((NoSpendDaysQuest) restored.get(1)).getTargetDays());
        assertEquals(5, ((LogDaysQuest) restored.get(2)).getTargetDays());
        assertEquals(250_000, ((SavingsQuest) restored.get(3)).getTargetAmount());
        ChoiceShareQuest share = (ChoiceShareQuest) restored.get(4);
        assertEquals("kind", share.getColumnId());
        assertEquals("Want", share.getOption());
        assertEquals(30, share.getMaxSharePercent());
        assertEquals(WEEK, restored.get(0).getPeriod());
        assertEquals(List.of("b"), back.rewardedQuests());
        assertEquals(LocalDate.of(2026, 11, 20), back.badges().get("streak-7"));
    }

    @Test
    void goalsRoundTrip(@TempDir Path dir) throws Exception {
        SavingsGoal g = new SavingsGoal(UUID.randomUUID(), "Laptop", 5_000_000, UUID.randomUUID());
        var repo = new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class);
        repo.saveAll(List.of(new GameStateDto(ProfileDto.from(new GameProfile()), List.of(), List.of(), Map.of(),
                List.of(GoalDto.from(g)))));
        assertEquals(g, repo.findAll().get(0).goals().get(0).toGoal());
    }

    @Test
    void aCorruptGameFileThrowsStorageExceptionAndKeepsACopy(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("game.json");
        Files.writeString(file, "{ not json");
        var repo = new JsonRepository<>(file, GameStateDto.class);
        assertThrows(StorageException.class, repo::findAll);
        try (var files = Files.list(dir)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    @Test
    void invalidProfileValuesAreRejectedOnRestore() {
        ProfileDto bad = new ProfileDto(-1, 0, 0, 0, null, null, null, null, 0);
        assertThrows(IllegalArgumentException.class, bad::toProfile);
    }
}
