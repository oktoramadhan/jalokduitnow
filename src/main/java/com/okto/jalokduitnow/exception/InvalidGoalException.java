package com.okto.jalokduitnow.exception;

/** User-facing error for savings goals (blank name, target <= 0, unknown category). */
public class InvalidGoalException extends Exception {
    public InvalidGoalException(String message) {
        super(message);
    }
}
