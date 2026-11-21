package com.okto.jalokduitnow.exception;

/** User-facing error for column definitions and column values (bad name, bad value, unconfirmed data loss). */
public class InvalidColumnException extends Exception {
    public InvalidColumnException(String message) {
        super(message);
    }
}
