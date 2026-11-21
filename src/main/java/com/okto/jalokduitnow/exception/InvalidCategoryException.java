package com.okto.jalokduitnow.exception;

/** User-facing error for categories (duplicate name, missing reassign target, bad color...). */
public class InvalidCategoryException extends Exception {
    public InvalidCategoryException(String message) {
        super(message);
    }
}
