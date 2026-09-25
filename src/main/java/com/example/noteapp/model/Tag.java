package com.example.noteapp.model;

import com.example.noteapp.exception.InvalidNoteException;

/** A single tag persisted in its own table and linked to notes many-to-many. */
public class Tag {

    public static final int MAX_NAME_LENGTH = 30;

    private long id;
    private String name;

    public Tag(String name) throws InvalidNoteException {
        this(0, name);
    }

    public Tag(long id, String name) throws InvalidNoteException {
        this.id = id;
        setName(name);
    }

    public long getId() { return id; }

    public void setId(long id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) throws InvalidNoteException {
        if (name == null || name.isBlank()) {
            throw new InvalidNoteException("Tag cannot be empty.");
        }
        String trimmed = name.strip().toLowerCase();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidNoteException("Tag is too long (max " + MAX_NAME_LENGTH + " characters).");
        }
        this.name = trimmed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Tag tag)) return false;
        return id > 0 && id == tag.id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }

    @Override
    public String toString() { return name; }
}
