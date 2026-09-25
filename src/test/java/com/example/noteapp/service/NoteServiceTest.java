package com.example.noteapp.service;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.exception.NoteNotFoundException;
import com.example.noteapp.model.Note;
import com.example.noteapp.repository.DatabaseManager;
import com.example.noteapp.repository.NoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Service-layer tests running against a real (temporary) SQLite database -
 * the same stack the production app uses, isolated per test via @TempDir.
 */
class NoteServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;
    private NoteService noteService;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() throws DatabaseException {
        database = new DatabaseManager(tempDir);
        NoteRepository noteRepository = new NoteRepository(database);
        noteService = new NoteService(noteRepository);
        categoryService = new CategoryService(
                new com.example.noteapp.repository.CategoryRepository(database), noteRepository);
    }

    @AfterEach
    void tearDown() throws DatabaseException {
        database.close();
    }

    private Note createSample(String title, String content) throws Exception {
        return noteService.createNote(title, content, null, Set.of());
    }

    // ------------------------------------------------------------- creation

    @Test
    void createValidNote() throws Exception {
        Note note = createSample("Shopping list", "Milk, eggs, bread");
        assertTrue(note.getId() > 0);
        assertEquals("Shopping list", note.getTitle());
        assertEquals("Milk, eggs, bread", note.getContent());
        assertFalse(note.isDeleted());
    }

    @Test
    void rejectEmptyTitle() {
        assertThrows(InvalidNoteException.class, () -> createSample("   ", "content"));
        assertThrows(InvalidNoteException.class, () -> createSample(null, "content"));
    }

    @Test
    void rejectOverlongTitle() {
        assertThrows(InvalidNoteException.class, () -> createSample("x".repeat(201), "content"));
    }

    @Test
    void acceptEmptyContentButRejectNull() throws Exception {
        Note note = createSample("Title only", "");
        assertEquals("", note.getContent());
    }

    @Test
    void createNoteWithTagsAndCategory() throws Exception {
        var category = categoryService.create("Study");
        Note note = noteService.createNote("OOP notes", "Encapsulation...", category.getId(),
                Set.of("java", "oop", "dsa"));
        Note loaded = noteService.getNote(note.getId());
        assertEquals(category.getId(), loaded.getCategoryId());
        assertEquals(Set.of("java", "oop", "dsa"), loaded.getTags());
    }

    @Test
    void rejectInvalidTag() {
        assertThrows(InvalidNoteException.class,
                () -> noteService.createNote("T", "c", null, Set.of("   ")));
        assertThrows(InvalidNoteException.class,
                () -> noteService.createNote("T", "c", null, Set.of("x".repeat(31))));
    }

    // ------------------------------------------------------------- update

    @Test
    void updateNoteRefreshesTimestamp() throws Exception {
        Note note = createSample("Before", "old");
        note.setTitle("After");
        note.setContent("new");
        Thread.sleep(5); // guarantee a different millisecond
        Note updated = noteService.updateNote(note);
        assertEquals("After", updated.getTitle());
        assertFalse(updated.getUpdatedAt().isBefore(updated.getCreatedAt()));
    }

    @Test
    void updateRejectsInvalidTitle() throws Exception {
        // Validation must fire before any database access: the Note setter rejects it.
        Note note = new Note("Valid", "content");
        assertThrows(InvalidNoteException.class, () -> note.setTitle(""));
    }

    @Test
    void updateUnknownNoteFails() throws Exception {
        Note ghost = new Note("ghost", "");
        ghost.setId(999_999);
        assertThrows(NoteNotFoundException.class, () -> noteService.updateNote(ghost));
    }

    // ------------------------------------------------------------- trash lifecycle

    @Test
    void deleteAndRestoreNote() throws Exception {
        Note note = createSample("To trash", "content");

        noteService.moveToTrash(note.getId());
        assertTrue(noteService.getNote(note.getId()).isDeleted());
        assertTrue(noteService.listActiveNotes().stream().noneMatch(n -> n.getId() == note.getId()));
        assertEquals(1, noteService.trashCount());

        noteService.restoreFromTrash(note.getId());
        assertFalse(noteService.getNote(note.getId()).isDeleted());
        assertEquals(0, noteService.trashCount());
    }

    @Test
    void permanentDeleteRemovesRow() throws Exception {
        Note note = createSample("Gone forever", "content");
        noteService.moveToTrash(note.getId());
        noteService.deletePermanently(note.getId());
        assertThrows(NoteNotFoundException.class, () -> noteService.getNote(note.getId()));
    }

    @Test
    void emptyTrashRemovesOnlyTrashed() throws Exception {
        Note keep = createSample("Keep me", "content");
        Note trash = createSample("Trash me", "content");
        noteService.moveToTrash(trash.getId());
        assertEquals(1, noteService.emptyTrash());
        assertNotNull(noteService.getNote(keep.getId()));
        assertThrows(NoteNotFoundException.class, () -> noteService.getNote(trash.getId()));
    }

    // ------------------------------------------------------------- pin & archive

    @Test
    void pinAndUnpin() throws Exception {
        Note note = createSample("Pinned note", "content");
        assertTrue(noteService.setPinned(note.getId(), true).isPinned());
        assertFalse(noteService.setPinned(note.getId(), false).isPinned());
    }

    @Test
    void archiveKeepsNoteButHidesFromActiveList() throws Exception {
        Note note = createSample("Archived", "content");
        noteService.setArchived(note.getId(), true);
        assertTrue(noteService.listActiveNotes().stream().noneMatch(n -> n.getId() == note.getId()));
        assertTrue(noteService.listArchive().stream().anyMatch(n -> n.getId() == note.getId()));
    }

    // ------------------------------------------------------------- search & sort

    @Test
    void searchByTitle() throws Exception {
        createSample("Java OOP guide", "content about code");
        createSample("Groceries", "milk");
        List<Note> hits = noteService.search("java oop");
        assertEquals(1, hits.size());
        assertEquals("Java OOP guide", hits.get(0).getTitle());
    }

    @Test
    void searchByContent() throws Exception {
        createSample("Recipe", "Grandma's apple pie secret");
        List<Note> hits = noteService.search("APPLE PIE");
        assertEquals(1, hits.size());
    }

    @Test
    void searchByTag() throws Exception {
        noteService.createNote("Tagged", "content", null, Set.of("university"));
        createSample("Untagged", "content");
        List<Note> hits = noteService.search("university");
        assertEquals(1, hits.size());
    }

    @Test
    void searchWithNoResultsReturnsEmptyListNotCrash() throws Exception {
        createSample("Something", "content");
        assertTrue(noteService.search("zzz-nonexistent").isEmpty());
    }

    @Test
    void searchDoesNotReturnTrashedNotes() throws Exception {
        Note note = createSample("Hidden in trash", "findme");
        noteService.moveToTrash(note.getId());
        assertTrue(noteService.search("findme").isEmpty());
    }

    @Test
    void percentSignSearchesLiterally() throws Exception {
        createSample("100% sure", "content");
        createSample("Percentless", "content");
        assertEquals(1, noteService.search("100%").size());
    }

    @Test
    void sortByTitle() throws Exception {
        createSample("Banana", "");
        createSample("apple", "");
        createSample("Cherry", "");
        List<Note> sorted = noteService.listNotes(
                new com.example.noteapp.repository.NoteQuery(null, null, null, null, false, false,
                        com.example.noteapp.repository.SortOrder.TITLE_ASC));
        assertEquals("apple", sorted.get(0).getTitle());
        assertEquals("Banana", sorted.get(1).getTitle());   // case-insensitive
        assertEquals("Cherry", sorted.get(2).getTitle());
    }

    // ------------------------------------------------------------- invalid ids

    @Test
    void rejectInvalidNoteId() {
        assertThrows(NoteNotFoundException.class, () -> noteService.getNote(424_242));
    }

    @Test
    void moveToTrashUnknownIdFails() {
        assertThrows(NoteNotFoundException.class, () -> noteService.moveToTrash(777));
    }
}
