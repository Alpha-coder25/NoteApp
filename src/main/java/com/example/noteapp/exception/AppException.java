package com.example.noteapp.exception;

/**
 * Root of the application's checked-exception hierarchy.
 *
 * <p>Every recoverable failure derives from here so the UI layer can catch a
 * single type ({@code AppException}) and show a friendly dialog, while the
 * technical cause chain is logged separately. Kept abstract because a bare
 * "application error" is never thrown directly - callers use a concrete subtype.
 */
public abstract class AppException extends Exception {

    protected AppException(String message) {
        super(message);
    }

    protected AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
