package com.okto.jalokduitnow.exception;

/** User-facing error for a setting that is out of range or inconsistent (day 40, equal quiet-hour times...). */
public class InvalidSettingException extends Exception {
    public InvalidSettingException(String message) {
        super(message);
    }
}
