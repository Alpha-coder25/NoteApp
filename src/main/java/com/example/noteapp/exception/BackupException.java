package com.example.noteapp.exception;

/** Thrown when creating a backup or restoring from one fails. */
public class BackupException extends AppException {

    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
