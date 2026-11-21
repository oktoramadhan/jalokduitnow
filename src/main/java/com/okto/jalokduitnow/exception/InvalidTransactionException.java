package com.okto.jalokduitnow.exception;

/** Checked exception for user-facing validation errors (e.g. negative amount). */
public class InvalidTransactionException extends Exception {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
