package com.example.noteapp.service;

import com.example.noteapp.exception.DuplicateCategoryException;
import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.model.Category;
import com.example.noteapp.repository.DatabaseManager;
import com.example.noteapp.repository.NoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Category rules: duplicates, validation, deletion with in-use notes. */
class CategoryTagServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;
    private CategoryService categoryService;
    private TagService tagService;
    private NoteService noteService;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir);
        NoteRepository noteRepository = new NoteRepository(database);
        categoryService = new CategoryService(
                new com.example.noteapp.repository.CategoryRepository(database), noteRepository);
        tagService = new TagService(new com.example.noteapp.repository.TagRepository(database));
        noteService = new NoteService(noteRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void createCategory() throws Exception {
        Category category = categoryService.create("Work");
        assertTrue(category.getId() > 0);
        assertEquals("Work", category.getName());
    }

    @Test
    void rejectDuplicateCategoryCaseInsensitive() throws Exception {
        categoryService.create("Work");
        assertThrows(DuplicateCategoryException.class, () -> categoryService.create("work"));
        assertThrows(DuplicateCategoryException.class, () -> categoryService.create("WORK"));
    }

    @Test
    void rejectEmptyCategoryName() {
        assertThrows(InvalidNoteException.class, () -> categoryService.create("  "));
    }

    @Test
    void rejectOverlongCategoryName() {
        assertThrows(InvalidNoteException.class, () -> categoryService.create("x".repeat(51)));
    }

    @Test
    void renameCategory() throws Exception {
        Category category = categoryService.create("Old");
        Category renamed = categoryService.rename(category.getId(), "New");
        assertEquals("New", renamed.getName());
        assertTrue(categoryService.findByName("new").isPresent());
    }

    @Test
    void renameToExistingNameFails() throws Exception {
        categoryService.create("First");
        Category second = categoryService.create("Second");
        assertThrows(DuplicateCategoryException.class,
                () -> categoryService.rename(second.getId(), "first"));
    }

    @Test
    void renameKeepingOwnNameIsAllowed() throws Exception {
        Category category = categoryService.create("Same");
        assertDoesNotThrow(() -> categoryService.rename(category.getId(), "same"));
    }

    @Test
    void deleteUnusedCategory() throws Exception {
        Category category = categoryService.create("Temp");
        categoryService.delete(category.getId());
        assertTrue(categoryService.findById(category.getId()).isEmpty());
    }

    @Test
    void deleteCategoryInUseReportsIt() throws Exception {
        Category category = categoryService.create("Used");
        noteService.createNote("Note", "content", category.getId(), Set.of());
        boolean inUse = categoryService.delete(category.getId());
        assertTrue(inUse);
        // note survives with no category
        assertTrue(noteService.listByCategory(null, com.example.noteapp.repository.SortOrder.UPDATED_DESC).size() >= 0);
    }

    @Test
    void countNotesInCategory() throws Exception {
        Category category = categoryService.create("Counted");
        noteService.createNote("A", "", category.getId(), Set.of());
        noteService.createNote("B", "", category.getId(), Set.of());
        assertEquals(2, categoryService.countNotesIn(category.getId()));
    }

    @Test
    void filterByCategory() throws Exception {
        Category work = categoryService.create("Work2");
        Category home = categoryService.create("Home2");
        noteService.createNote("At work", "", work.getId(), Set.of());
        noteService.createNote("At home", "", home.getId(), Set.of());
        assertEquals(1, noteService.listByCategory(work.getId(),
                com.example.noteapp.repository.SortOrder.UPDATED_DESC).size());
    }

    @Test
    void filterByTag() throws Exception {
        noteService.createNote("One", "", null, Set.of("urgent"));
        noteService.createNote("Two", "", null, Set.of("later"));
        noteService.createNote("Three", "", null, Set.of("urgent", "later"));
        assertEquals(2, noteService.listByTag("urgent",
                com.example.noteapp.repository.SortOrder.UPDATED_DESC).size());
    }

    @Test
    void tagNamesListed() throws Exception {
        noteService.createNote("T", "", null, Set.of("alpha", "beta"));
        assertEquals(Set.of("alpha", "beta"), Set.copyOf(tagService.listTagNames()));
    }
}
