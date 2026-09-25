package com.example.noteapp.exception;

/** Thrown when creating or renaming a category to a name that already exists. */
public class DuplicateCategoryException extends AppException {

    public DuplicateCategoryException(String name) {
        super("A category named \"" + name + "\" already exists.");
    }
}
