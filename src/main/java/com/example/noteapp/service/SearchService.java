package com.example.noteapp.service;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.model.Note;
import com.example.noteapp.repository.NoteQuery;
import com.example.noteapp.repository.NoteRepository;
import com.example.noteapp.repository.SortOrder;
import com.example.noteapp.util.Searchable;

import java.util.List;

/**
 * Case-insensitive search across titles, content, categories and tags
 * (implements the {@link Searchable} abstraction).
 *
 * <p>The actual matching happens in SQL inside {@link NoteRepository}; this
 * class owns the query construction and result loading so the UI and other
 * services don't need to know about {@link NoteQuery} internals.
 */
public class SearchService implements Searchable {

    private final NoteRepository noteRepository;

    public SearchService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /**
     * Runs a full search over active (non-deleted, non-archived) notes.
     *
     * @return matching notes - empty list, never {@code null}, when nothing matches
     */
    @Override
    public List<Note> search(String query) throws DatabaseException {
        return search(query, SortOrder.UPDATED_DESC);
    }

    /** Search with an explicit sort order (the UI sort selector applies to results). */
    public List<Note> search(String query, SortOrder order) throws DatabaseException {
        List<Note> notes = noteRepository.findBy(
                new NoteQuery(query, null, null, null, false, false, order));
        noteRepository.loadTags(notes);
        return notes;
    }

    /** Search restricted to the trash view (restore-by-search). */
    public List<Note> searchTrash(String query, SortOrder order) throws DatabaseException {
        List<Note> notes = noteRepository.findBy(
                new NoteQuery(query, null, null, null, null, true, order));
        noteRepository.loadTags(notes);
        return notes;
    }
}
