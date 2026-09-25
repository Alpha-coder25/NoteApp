package com.example.noteapp.repository;

import java.util.Locale;

/**
 * Immutable query object describing which notes to list. Built by the service
 * layer from UI filters; the repository turns it into a parameterized WHERE
 * clause. {@code null} means "no filter" for optional criteria.
 */
public record NoteQuery(
        String text,          // free-text search across title/content/category/tags
        Long categoryId,      // exact category
        String tag,           // exact tag name (case-insensitive)
        Boolean pinned,       // pinned filter
        Boolean archived,     // archived filter
        Boolean deleted,      // trash filter (false = exclude trashed notes)
        SortOrder sortOrder) {

    /** Default listing: active notes, most recently modified first. */
    public static NoteQuery active() {
        return new NoteQuery(null, null, null, null, false, false, SortOrder.UPDATED_DESC);
    }

    public NoteQuery {
        if (text != null) {
            String trimmed = text.strip();
            text = trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
        }
        if (tag != null) {
            String trimmedTag = tag.strip();
            tag = trimmedTag.isEmpty() ? null : trimmedTag.toLowerCase(Locale.ROOT);
        }
        if (sortOrder == null) {
            sortOrder = SortOrder.UPDATED_DESC;
        }
    }

    /** True when no criterion narrows the listing (used to skip WHERE building). */
    public boolean isUnfiltered() {
        return text == null && categoryId == null && tag == null
                && pinned == null && archived == null && deleted == null;
    }
}
