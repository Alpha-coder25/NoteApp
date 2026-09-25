package com.example.noteapp.util;

import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.model.Category;
import com.example.noteapp.model.Note;
import com.example.noteapp.model.Tag;

/**
 * Centralized input validation (single responsibility) so the same rules are
 * applied no matter whether input arrives from the UI, an import file or a test.
 */
public final class Validator {

    /** PIN: 4-8 numeric digits. Enforced here and mirrored by the UI. */
    public static final int PIN_MIN_LENGTH = 4;
    public static final int PIN_MAX_LENGTH = 8;

    private Validator() { }

    public static void validateNote(Note note) throws InvalidNoteException {
        if (note == null) {
            throw new InvalidNoteException("Note cannot be null.");
        }
        // Setters already validate; calling them re-runs the checks in one place.
        note.setTitle(note.getTitle());
        note.setContent(note.getContent());
    }

    public static void validateCategory(Category category) throws InvalidNoteException {
        if (category == null) {
            throw new InvalidNoteException("Category cannot be null.");
        }
        category.setName(category.getName());
    }

    public static void validateTag(Tag tag) throws InvalidNoteException {
        if (tag == null) {
            throw new InvalidNoteException("Tag cannot be null.");
        }
        tag.setName(tag.getName());
    }

    /**
     * Validates a candidate application-lock PIN.
     *
     * @throws InvalidNoteException when the PIN is not 4-8 digits
     */
    public static void validatePin(String pin) throws InvalidNoteException {
        if (pin == null || !pin.matches("\\d{" + PIN_MIN_LENGTH + "," + PIN_MAX_LENGTH + "}")) {
            throw new InvalidNoteException(
                    "PIN must be " + PIN_MIN_LENGTH + " to " + PIN_MAX_LENGTH + " digits.");
        }
    }

    /**
     * Parses an integer setting, falling back to a default when absent or malformed.
     * Invalid settings must never crash the app at start-up.
     */
    public static int parseIntSetting(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
