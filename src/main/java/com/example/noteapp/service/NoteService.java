package com.example.noteapp.service;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.exception.NoteNotFoundException;
import com.example.noteapp.model.Note;
import com.example.noteapp.repository.NoteQuery;
import com.example.noteapp.repository.NoteRepository;
import com.example.noteapp.repository.SortOrder;
import com.example.noteapp.util.Validator;

import java.util.List;

/**
 * Business rules for notes (creation, editing, pin/archive, trash lifecycle,
 * listing). This is the only class the UI talks to about notes - the UI never
 * touches repositories or JDBC (layered architecture).
 */
public class NoteService {

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    // ------------------------------------------------------------------ CRUD

    /** Validates and creates a note, returning it with its new id. */
    public Note createNote(String title, String content, Long categoryId, java.util.Set<String> tags)
            throws InvalidNoteException, DatabaseException {
        Note note = new Note(title, content);
        note.setCategoryId(categoryId);
        note.setTags(tags);
        Validator.validateNote(note);
        Note saved = noteRepository.save(note);
        noteRepository.loadTags(saved);
        return saved;
    }

    /** Loads a note (including trashed/archived ones) with its tags. */
    public Note getNote(long id) throws NoteNotFoundException, DatabaseException {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new NoteNotFoundException(id));
        noteRepository.loadTags(note);
        return note;
    }

    /**
     * Persists edits to an existing note and refreshes its tag list.
     * {@code updatedAt} is set by the repository on save.
     */
    public Note updateNote(Note note) throws NoteNotFoundException, DatabaseException, InvalidNoteException {
        if (note.isNew() || !noteRepository.existsById(note.getId())) {
            throw new NoteNotFoundException(note.getId());
        }
        Validator.validateNote(note);
        noteRepository.save(note);
        noteRepository.loadTags(note);
        return note;
    }

    // ------------------------------------------------------------------ trash lifecycle

    /** Soft-deletes: the note moves to the trash and can be restored. */
    public void moveToTrash(long id) throws NoteNotFoundException, DatabaseException {
        Note note = getNote(id);
        note.setDeleted(true);
        noteRepository.save(note);
    }

    /** Returns a trashed note to the active list. */
    public void restoreFromTrash(long id) throws NoteNotFoundException, DatabaseException {
        Note note = getNote(id);
        note.setDeleted(false);
        noteRepository.save(note);
    }

    /** Permanent removal; confirmation is the UI's responsibility. */
    public void deletePermanently(long id) throws DatabaseException {
        noteRepository.hardDelete(id);
    }

    /** Permanently deletes every trashed note; confirmation is the UI's responsibility. */
    public int emptyTrash() throws DatabaseException {
        return noteRepository.hardDeleteAllDeleted();
    }

    // ------------------------------------------------------------------ state toggles

    public Note setPinned(long id, boolean pinned) throws NoteNotFoundException, DatabaseException {
        Note note = getNote(id);
        note.setPinned(pinned);
        return noteRepository.save(note);
    }

    public Note setArchived(long id, boolean archived) throws NoteNotFoundException, DatabaseException {
        Note note = getNote(id);
        note.setArchived(archived);
        return noteRepository.save(note);
    }

    // ------------------------------------------------------------------ listing

    /** Lists notes matching the query (filtering + sorting + search happen in SQL). */
    public List<Note> listNotes(NoteQuery query) throws DatabaseException {
        List<Note> notes = noteRepository.findBy(query);
        noteRepository.loadTags(notes);
        return notes;
    }

    public List<Note> listActiveNotes() throws DatabaseException {
        return listNotes(NoteQuery.active());
    }

    public List<Note> listTrash() throws DatabaseException {
        return listNotes(new NoteQuery(null, null, null, null, null, true, SortOrder.UPDATED_DESC));
    }

    public List<Note> listArchive() throws DatabaseException {
        return listNotes(new NoteQuery(null, null, null, null, true, false, SortOrder.UPDATED_DESC));
    }

    public List<Note> listPinned() throws DatabaseException {
        return listNotes(new NoteQuery(null, null, null, true, false, false, SortOrder.UPDATED_DESC));
    }

    /** Convenience search used by tests and quick filters. */
    public List<Note> search(String text) throws DatabaseException {
        return listNotes(new NoteQuery(text, null, null, null, false, false, SortOrder.UPDATED_DESC));
    }

    /** Active notes in one category, sorted as requested. */
    public List<Note> listByCategory(Long categoryId, SortOrder order) throws DatabaseException {
        return listNotes(new NoteQuery(null, categoryId, null, null, false, false, order));
    }

    /** Active notes carrying one tag, sorted as requested. */
    public List<Note> listByTag(String tag, SortOrder order) throws DatabaseException {
        return listNotes(new NoteQuery(null, null, tag, null, false, false, order));
    }

    /** Number of notes in the trash, for the sidebar badge. */
    public long trashCount() throws DatabaseException {
        return noteRepository.countDeleted();
    }
}
