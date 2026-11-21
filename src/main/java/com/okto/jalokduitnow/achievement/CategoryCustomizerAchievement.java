package com.okto.jalokduitnow.achievement;

import com.okto.jalokduitnow.service.CategoryService;

/** Make the app yours: have at least {@code minCategories} categories that are not among the built-in defaults. */
public class CategoryCustomizerAchievement extends Achievement {
    private final int minCategories;

    public CategoryCustomizerAchievement(int minCategories) {
        super("category-customizer", "Penata Kategori",
                "Buat minimal " + minCategories + " kategori sendiri", "categories");
        this.minCategories = minCategories;
    }

    @Override
    public boolean check(GameContext c) {
        long custom = c.categories().stream()
                .filter(cat -> !cat.isArchived())
                .filter(cat -> !cat.getName().equalsIgnoreCase(CategoryService.FALLBACK_NAME))
                .filter(cat -> CategoryService.DEFAULT_NAMES.stream().noneMatch(d -> d.equalsIgnoreCase(cat.getName())))
                .count();
        return custom >= minCategories;
    }
}
