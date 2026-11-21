package com.okto.jalokduitnow.model;

import com.okto.jalokduitnow.exception.InvalidCategoryException;

import java.util.UUID;

/**
 * Lets the storage layer turn saved category data into valid category ids while loading.
 * Needed to migrate files from older versions (which saved only a category name) and to repair ids
 * that no longer exist, so a loaded transaction never points at a missing category.
 */
public interface CategoryResolver {
    /** Id of the category with this name and type; creates it if it does not exist yet. */
    UUID idFor(CategoryType type, String legacyName) throws InvalidCategoryException;

    /** The id itself if it is a known category of this type; otherwise the "Tanpa Kategori" fallback. */
    UUID resolve(UUID id, CategoryType type) throws InvalidCategoryException;
}
