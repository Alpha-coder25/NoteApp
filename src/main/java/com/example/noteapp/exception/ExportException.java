package com.example.noteapp.exception;

/** Thrown when writing notes to an external file fails. */
public class ExportException extends AppException {

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
