package com.okto.jalokduitnow.exception;

/** User-facing error for recipients: blank name, phone number that is not numeric or has a bad length. */
public class InvalidRecipientException extends Exception {
    public InvalidRecipientException(String message) {
        super(message);
    }
}
