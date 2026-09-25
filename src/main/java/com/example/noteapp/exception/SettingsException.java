package com.example.noteapp.exception;

/** Thrown when a persisted settings value is missing or fails validation. */
public class SettingsException extends AppException {

    public SettingsException(String message) {
        super(message);
    }

    public SettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
