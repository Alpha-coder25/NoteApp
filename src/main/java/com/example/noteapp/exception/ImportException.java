package com.example.noteapp.exception;

/** Thrown when an import file is missing, unreadable or structurally invalid. */
public class ImportException extends AppException {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
