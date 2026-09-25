package com.example.noteapp.model;

import com.example.noteapp.exception.InvalidNoteException;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Core domain object. Demonstrates <b>encapsulation</b>: every field is private
 * and mutated only through validating setters, so a {@code Note} can never exist
 * in an invalid state (empty/oversized title, null content, ...).
 *
 * <p>Kept completely free of UI and JDBC types so it can be unit-tested and
 * reused by exporters without any framework dependency.
 */
public class Note {

    /** Hard limits shared by the editor UI and the validator. */
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_CONTENT_LENGTH = 1_000_000;

    private long id;
    private String title;
    private String content;
    private Long categoryId;                 // null = uncategorized
    private Instant createdAt;
    private Instant updatedAt;
    private boolean pinned;
    private boolean archived;
    private boolean deleted;                 // soft-delete flag (trash)
    private final Set<String> tags = new LinkedHashSet<>();

    /** Fresh, unsaved note ("new note" state before the repository assigns an id). */
    public Note(String title, String content) throws InvalidNoteException {
        this(0, title, content, null, Instant.now(), Instant.now(), false, false, false);
    }

    /** Full constructor used by the repository when hydrating rows from SQLite. */
    public Note(long id, String title, String content, Long categoryId,
                Instant createdAt, Instant updatedAt,
                boolean pinned, boolean archived, boolean deleted) throws InvalidNoteException {
        this.id = id;
        setTitle(title);
        setContent(content);
        this.categoryId = categoryId;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
        this.pinned = pinned;
        this.archived = archived;
        this.deleted = deleted;
    }

    // ---------------------------------------------------------------- accessors

    public long getId() { return id; }

    public void setId(long id) { this.id = id; }

    /** True while the note has never been written to the database. */
    public boolean isNew() { return id <= 0; }

    public String getTitle() { return title; }

    public void setTitle(String title) throws InvalidNoteException {
        if (title == null || title.isBlank()) {
            throw new InvalidNoteException("Title cannot be empty.");
        }
        String trimmed = title.strip();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidNoteException(
                    "Title is too long (" + trimmed.length() + " characters). Maximum is " + MAX_TITLE_LENGTH + ".");
        }
        this.title = trimmed;
    }

    public String getContent() { return content; }

    /** Content may be empty (a title-only note is allowed) but never null or oversized. */
    public void setContent(String content) throws InvalidNoteException {
        if (content == null) {
            content = "";
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new InvalidNoteException(
                    "Note is too large (" + content.length() + " characters). Maximum is " + MAX_CONTENT_LENGTH + ".");
        }
        this.content = content;
    }

    public Long getCategoryId() { return categoryId; }

    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Instant getCreatedAt() { return createdAt; }

    /** Used by import to preserve original timestamps. */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public Instant getUpdatedAt() { return updatedAt; }

    /** Used by import to preserve original timestamps; {@code touch()} supersedes it. */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    /** Called by the service layer after every persisted mutation. */
    public void touch() { this.updatedAt = Instant.now(); }

    public boolean isPinned() { return pinned; }

    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public boolean isArchived() { return archived; }

    public void setArchived(boolean archived) { this.archived = archived; }

    public boolean isDeleted() { return deleted; }

    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    // ---------------------------------------------------------------- tags

    /** Read-only view; mutate through {@link #addTag}/{@link #removeTag}. */
    public Set<String> getTags() { return Collections.unmodifiableSet(tags); }

    public void addTag(String tag) throws InvalidNoteException {
        String normalized = normalizeTag(tag);
        if (normalized == null) {
            throw new InvalidNoteException("Tags cannot be empty and must be at most 30 characters.");
        }
        tags.add(normalized);
    }

    public void removeTag(String tag) {
        String normalized = normalizeTag(tag);
        if (normalized != null) {
            tags.remove(normalized);
        }
    }

    public void setTags(Set<String> newTags) throws InvalidNoteException {
        tags.clear();
        if (newTags == null) {
            return;
        }
        for (String tag : newTags) {
            addTag(tag);
        }
    }

    /** Trims, lower-cases and length-checks a single tag; returns null when invalid. */
    private static String normalizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        String trimmed = tag.strip().toLowerCase();
        if (trimmed.isEmpty() || trimmed.length() > 30) {
            return null;
        }
        return trimmed;
    }

    // ---------------------------------------------------------------- object contract

    /** Identity semantics: two notes with the same id are the same note. */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Note note)) return false;
        return id > 0 && id == note.id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }

    /** Deliberately excludes content - notes are private data, log lines are not. */
    @Override
    public String toString() {
        return "Note{id=" + id + ", title=" + title + ", pinned=" + pinned
                + ", archived=" + archived + ", deleted=" + deleted + "}";
    }
}
