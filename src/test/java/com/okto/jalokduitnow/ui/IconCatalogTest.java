package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.model.Category;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The icon catalog is plain data (keys -> Ikon constants), so it can be tested without a JavaFX toolkit. */
class IconCatalogTest {
    @Test
    void containsTheStableKeysTheAppDependsOn() {
        for (String key : List.of("food", "transport", "home", "school", "health", "savings", "shopping", "bills",
                "entertainment", "other", "flame", "shield", "star", "trophy", "target", "settings")) {
            assertTrue(IconCatalog.exists(key), "missing icon key: " + key);
        }
    }

    @Test
    void unknownKeysFallBackToOther() {
        assertFalse(IconCatalog.exists("no-such-icon"));
        assertEquals("other", IconCatalog.normalize("no-such-icon"));
        assertEquals("other", IconCatalog.normalize(null));
        assertSame(IconCatalog.ikon("other"), IconCatalog.ikon("no-such-icon"));
    }

    @Test
    void pickerOffersOnlyCategoryIconsAndIncludesTheDefault() {
        List<String> keys = IconCatalog.categoryKeys();
        assertTrue(keys.contains(Category.DEFAULT_ICON));
        assertTrue(keys.contains("food"));
        assertFalse(keys.contains("flame"));            // app icons are not category choices
        assertEquals(keys.size(), keys.stream().distinct().count());
        assertTrue(keys.stream().allMatch(IconCatalog::exists));
    }

    @Test
    void everySeededCategoryIconExists() {
        for (String key : List.of("food", "transport", "shopping", "entertainment", "bills", "health", "savings",
                "salary", "freelance", "gift", "work")) {
            assertTrue(IconCatalog.categoryKeys().contains(key), key);
        }
    }
}
