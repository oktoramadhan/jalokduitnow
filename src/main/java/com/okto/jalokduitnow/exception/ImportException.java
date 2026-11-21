package com.okto.jalokduitnow.exception;

/** Thrown when importing the Excel tracker fails. */
public class ImportException extends Exception {
    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
