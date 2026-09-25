package com.example.noteapp.exception;

/** Thrown when a note fails validation before it reaches the database. */
public class InvalidNoteException extends NoteException {

    public InvalidNoteException(String message) {
        super(message);
    }
}
