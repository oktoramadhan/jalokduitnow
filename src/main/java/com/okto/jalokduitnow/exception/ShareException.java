package com.okto.jalokduitnow.exception;

/** Sharing a report failed (no browser available, message too long, bad number...). */
public class ShareException extends Exception {
    public ShareException(String message) {
        super(message);
    }

    public ShareException(String message, Throwable cause) {
        super(message, cause);
    }
}
