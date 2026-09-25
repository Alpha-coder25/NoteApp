package com.example.noteapp.model;

import com.example.noteapp.exception.InvalidNoteException;

/** A user-defined grouping for notes (Work, Study, ...). Immutable after rename. */
public class Category {

    public static final int MAX_NAME_LENGTH = 50;

    private long id;
    private String name;

    public Category(String name) throws InvalidNoteException {
        this(0, name);
    }

    public Category(long id, String name) throws InvalidNoteException {
        this.id = id;
        setName(name);
    }

    public long getId() { return id; }

    public void setId(long id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) throws InvalidNoteException {
        if (name == null || name.isBlank()) {
            throw new InvalidNoteException("Category name cannot be empty.");
        }
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidNoteException("Category name is too long (max " + MAX_NAME_LENGTH + " characters).");
        }
        this.name = trimmed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Category category)) return false;
        return id > 0 && id == category.id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }

    @Override
    public String toString() { return name; }
}
