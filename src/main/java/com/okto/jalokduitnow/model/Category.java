package com.okto.jalokduitnow.model;

/** Immutable category value. Encapsulation: name is normalized once, on construction. */
public record Category(String name) {
    public Category {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        name = name.trim();
    }
}
