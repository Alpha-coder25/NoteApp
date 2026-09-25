package com.example.noteapp.exception;

/** Base type for all note-domain failures (validation, lookup, state). */
public class NoteException extends AppException {

    public NoteException(String message) {
        super(message);
    }

    public NoteException(String message, Throwable cause) {
        super(message, cause);
    }
}
