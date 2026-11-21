package com.okto.jalokduitnow.restore;

import com.okto.jalokduitnow.event.GameEvent;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.model.Expense;
import com.okto.jalokduitnow.schema.TableSchema;
import com.okto.jalokduitnow.schema.TextType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The reload hooks that a restore (and a failed restore's rollback) rely on. */
class ReloadTest {
    @TempDir Path dir;

    @Test
    void theSchemaIsReloadedIntoTheSameObjectItsUsersHold() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir);
        TableSchema same = w.fx.schema.getSchema();
        w.fx.schema.addColumn("Lama", new TextType(), false);
        w.flush();
        assertEquals(2, same.getColumns().size());

        Files.writeString(dir.resolve("schema.json"),
                "[{\"id\":\"keterangan\",\"name\":\"Keterangan\",\"visible\":true,\"required\":false,\"type\":{\"type\":\"TEXT\"}}]");
        int[] told = {0};
        w.fx.schema.addListener(() -> told[0]++);
        w.fx.schema.reload();

        assertSame(same, w.fx.schema.getSchema(), "FinanceService and the dialogs keep working with the same object");
        assertEquals(1, same.getColumns().size(), "the old column is gone and the list is not duplicated");
        assertTrue(told[0] > 0);
    }

    @Test
    void reloadingTwiceDoesNotDuplicateAnything() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir).seed(4, "X");
        int tx = w.fx.finance.getAll().size(), cats = w.fx.categories.getAll().size();
        int cols = w.fx.schema.getSchema().getColumns().size(), rec = w.recipients.getAll().size();
        w.flush();
        w.reloader.reloadAll();
        w.reloader.reloadAll();
        assertEquals(tx, w.fx.finance.getAll().size());
        assertEquals(cats, w.fx.categories.getAll().size());
        assertEquals(cols, w.fx.schema.getSchema().getColumns().size());
        assertEquals(rec, w.recipients.getAll().size());
        assertEquals(1, w.game.goals().size());
    }

    @Test
    void theGameStateIsReadAgainFromDiskAndNothingIsAwarded() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir).seed(6, "G");
        long xp = w.game.profile().getXp();
        int badges = w.game.unlockedCount();
        w.flush();
        w.events.clear();

        w.game.reload();
        assertEquals(xp, w.game.profile().getXp());
        assertEquals(badges, w.game.unlockedCount());
        assertEquals(1, w.game.goals().size());
        assertTrue(w.events.isEmpty(), "reloading publishes no events");
    }

    @Test
    void aReloadForgetsStateThatIsNotInTheFile() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir).seed(6, "G");
        w.flush();
        Files.delete(dir.resolve("game.json"));                          // the saved game state is gone
        w.game.reload();
        assertEquals(0, w.game.unlockedCount());
        assertTrue(w.game.goals().isEmpty(), "goals are not kept from memory");
    }

    @Test
    void suspendedRewardsBlockBadgesFromColumnAndCategoryChangesUntilResumed() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir);
        w.game.suspendRewards(true);
        w.fx.schema.addColumn("A", new TextType(), false);               // would unlock the column-builder badge
        assertEquals(0, w.game.unlockedCount());
        assertTrue(w.events.stream().noneMatch(e -> e instanceof GameEvent.BadgeUnlocked));
        w.game.suspendRewards(false);
        w.fx.schema.addColumn("B", new TextType(), false);               // normal play: the badge is earned
        assertEquals(1, w.game.unlockedCount());
    }

    @Test
    void theRewardsAreResumedEvenIfTheReloadFails() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir).seed(2, "F");
        w.flush();
        Files.writeString(dir.resolve("categories.json"), "{ this is not json");
        assertThrows(Exception.class, () -> w.reloader.reloadAll());
        w.fx.categories.add("Baru", CategoryType.EXPENSE, "food", "#112233", null);   // would normally be rewarded-eligible
        w.fx.finance.add(new Expense(LocalDate.of(2026, 11, 21), w.fx.categories.getAll().get(0).getId(), 1_000));
        assertTrue(w.game.profile().getXp() > 0, "rewards were switched back on in the finally block");
    }

    @Test
    void reminderSettingsGoBackToDefaultsWhenTheFileIsMissing() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir);
        w.reminders.setBudgetWarnPercent(55);
        w.flush();
        Files.delete(dir.resolve("reminders.json"));
        w.reminders.reload();
        assertEquals(80, w.reminders.current().budgetWarnPercent());
    }

    @Test
    void recipientsAndDonorCategoriesAreReloaded() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir).seed(2, "R");
        UUID donorCategory = w.donors.donorCategoryIds().iterator().next();
        w.flush();
        Files.writeString(dir.resolve("recipients.json"), "[]");
        Files.writeString(dir.resolve("donor-settings.json"), "[{\"donorCategoryIds\":[]}]");
        w.recipients.reload();
        w.donors.reload();
        assertTrue(w.recipients.getAll().isEmpty());
        assertFalse(w.donors.isDonorCategory(donorCategory));
    }

    @Test
    void transactionsAreReloadedAndSorted() throws Exception {
        RestoreWorld w = RestoreWorld.create(dir).seed(5, "T");
        int before = w.fx.finance.getAll().size();
        w.flush();
        w.reloader.reloadAll();
        assertEquals(before, w.fx.finance.getAll().size());
        var dates = w.fx.finance.getAll().stream().map(t -> t.getDate()).toList();
        assertEquals(dates.stream().sorted(java.util.Comparator.reverseOrder()).toList(), dates, "newest first, as always");
        assertNotEquals(List.of(), dates);
    }
}
