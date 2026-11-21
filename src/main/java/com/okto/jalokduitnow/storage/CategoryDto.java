package com.okto.jalokduitnow.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.okto.jalokduitnow.exception.InvalidCategoryException;
import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryType;

import java.util.Map;
import java.util.UUID;

/**
 * JSON form of a Category. The icon is saved as a key. {@code emoji} only exists to read files saved by
 * older versions; it is never written.
 */
public record CategoryDto(UUID id, String name, String type, String iconKey, String color,
                          Long monthlyBudget, boolean archived,
                          @JsonInclude(JsonInclude.Include.NON_NULL) String emoji) {

    /**
     * Old files stored an emoji. The app's own default categories map to their matching icon; any other
     * value becomes "other". The emojis are written as unicode escapes so no emoji character lives in the source.
     */
    private static final Map<String, String> LEGACY_DEFAULT_EMOJI = Map.ofEntries(
            Map.entry("\uD83C\uDF5C", "food"),          // steaming bowl
            Map.entry("\uD83D\uDE8C", "transport"),     // bus
            Map.entry("\uD83D\uDECD", "shopping"),      // shopping bags
            Map.entry("\uD83C\uDFAE", "entertainment"), // video game
            Map.entry("\uD83D\uDCA1", "bills"),         // light bulb
            Map.entry("\uD83D\uDC8A", "health"),        // pill
            Map.entry("\uD83C\uDFE6", "savings"),       // bank
            Map.entry("\uD83D\uDCBC", "work"),          // briefcase
            Map.entry("\uD83D\uDCBB", "freelance"),     // laptop
            Map.entry("\uD83C\uDF81", "gift"));         // wrapped gift

    public static CategoryDto from(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.getType().label(), c.getIconKey(), c.getColor(),
                c.getMonthlyBudget(), c.isArchived(), null);
    }

    public Category toCategory() throws InvalidCategoryException {
        try {
            return new Category(id, name, CategoryType.fromLabel(type), resolveIconKey(), color, monthlyBudget, archived);
        } catch (IllegalArgumentException e) {
            throw new InvalidCategoryException(e.getMessage());
        }
    }

    /** True if this record came from an old file and should be saved again in the new format. */
    @JsonIgnore // otherwise Jackson would save it as a property and fail to read it back
    public boolean isLegacy() {
        return iconKey == null || emoji != null;
    }

    private String resolveIconKey() {
        if (iconKey != null && !iconKey.isBlank()) return iconKey;
        if (emoji == null) return Category.DEFAULT_ICON;
        String stripped = emoji.replace("\uFE0F", "").trim(); // drop the emoji variation selector
        return LEGACY_DEFAULT_EMOJI.getOrDefault(stripped, Category.DEFAULT_ICON);
    }
}
