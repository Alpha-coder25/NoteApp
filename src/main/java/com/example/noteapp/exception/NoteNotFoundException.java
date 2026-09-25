package com.example.noteapp.exception;

/** Thrown when a note id does not match any row in the database. */
public class NoteNotFoundException extends NoteException {

    public NoteNotFoundException(long id) {
        super("Note not found (id=" + id + "). It may have been permanently deleted.");
    }
}
