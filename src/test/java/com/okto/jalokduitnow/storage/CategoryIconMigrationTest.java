package com.okto.jalokduitnow.storage;

import com.okto.jalokduitnow.Fixture;
import com.okto.jalokduitnow.model.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Old category files stored an emoji; they must load with icon keys and be re-saved. */
class CategoryIconMigrationTest {
    // emojis are built from code points so no emoji character appears in the source
    private static final String RAMEN = new String(Character.toChars(0x1F35C));
    private static final String UNKNOWN = new String(Character.toChars(0x1F984));
    private static final String BAG_WITH_SELECTOR = new String(Character.toChars(0x1F6CD)) + new String(Character.toChars(0xFE0F));

    private static String legacy(String name, String emoji) {
        return """
                {"id":"%s","name":"%s","type":"Expense","emoji":"%s","color":"#f0a030","monthlyBudget":null,"archived":false}"""
                .formatted(UUID.randomUUID(), name, emoji);
    }

    @Test
    void oldEmojisBecomeIconKeysAndTheFileIsRewritten(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("categories.json"), "[" + String.join(",",
                legacy("Makan", RAMEN),                 // one of the app's own defaults: keeps a matching icon
                legacy("Belanja", BAG_WITH_SELECTOR),   // variation selector must not break the match
                legacy("Aneh", UNKNOWN)) + "]");        // anything else becomes "other"

        Fixture fx = Fixture.in(dir);
        assertEquals("food", icon(fx, "Makan"));
        assertEquals("shopping", icon(fx, "Belanja"));
        assertEquals("other", icon(fx, "Aneh"));

        fx.reload();
        String json = Files.readString(dir.resolve("categories.json"));
        assertTrue(json.contains("iconKey"));
        assertFalse(json.contains("emoji"));
        assertEquals("food", icon(Fixture.in(dir), "Makan"));
    }

    private static String icon(Fixture fx, String name) {
        return fx.categories.getAll().stream().filter(c -> c.getName().equals(name)).findFirst()
                .map(Category::getIconKey).orElseThrow();
    }

    @Test
    void modernFileKeepsItsIconKeys(@TempDir Path dir) throws Exception {
        Fixture fx = Fixture.in(dir);
        UUID id = fx.categories.add("Hobi", com.okto.jalokduitnow.model.CategoryType.EXPENSE, "music", "#8a63d2", null).getId();
        assertEquals("music", fx.reload().categories.require(id).getIconKey());
    }
}
