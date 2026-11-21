package com.okto.jalokduitnow.exception;

/** Thrown when reading/writing the local data store fails. */
public class StorageException extends Exception {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
