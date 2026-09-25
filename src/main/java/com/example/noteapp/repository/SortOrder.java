package com.example.noteapp.repository;

/**
 * Sort options for note listings. Order of listing matches the UI combo box.
 */
public enum SortOrder {
    UPDATED_DESC("Last modified first"),
    UPDATED_ASC("Oldest modified first"),
    CREATED_DESC("Newest first"),
    CREATED_ASC("Oldest first"),
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A");

    private final String label;

    SortOrder(String label) { this.label = label; }

    /** Human-readable label for the sort combo box. */
    public String getLabel() { return label; }

    /** SQL ORDER BY fragment; ISO-8601 timestamps sort correctly as text. */
    public String toSql() {
        return switch (this) {
            case TITLE_ASC -> "LOWER(n.title) ASC";
            case TITLE_DESC -> "LOWER(n.title) DESC";
            case CREATED_DESC -> "n.created_at DESC";
            case CREATED_ASC -> "n.created_at ASC";
            case UPDATED_DESC -> "n.updated_at DESC";
            case UPDATED_ASC -> "n.updated_at ASC";
        };
    }
}
