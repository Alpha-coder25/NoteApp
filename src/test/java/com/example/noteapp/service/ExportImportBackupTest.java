package com.example.noteapp.service;

import com.example.noteapp.exception.ImportException;
import com.example.noteapp.model.AppSettings;
import com.example.noteapp.model.Note;
import com.example.noteapp.repository.CategoryRepository;
import com.example.noteapp.repository.DatabaseManager;
import com.example.noteapp.repository.NoteRepository;
import com.example.noteapp.repository.SettingsRepository;
import com.example.noteapp.service.ExportService;
import com.example.noteapp.service.ImportService;
import com.example.noteapp.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Export (TXT/JSON via polymorphic Exporter), import validation and backup restore. */
class ExportImportBackupTest {

    @TempDir
    Path tempDir;

    private DatabaseManager database;
    private NoteService noteService;
    private CategoryService categoryService;
    private ExportService exportService;
    private ImportService importService;
    private com.example.noteapp.service.BackupService backupService;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir);
        NoteRepository noteRepository = new NoteRepository(database);
        noteService = new NoteService(noteRepository);
        categoryService = new CategoryService(new CategoryRepository(database), noteRepository);
        exportService = new ExportService(noteRepository, new CategoryRepository(database));
        importService = new ImportService(noteRepository, categoryService);
        backupService = new com.example.noteapp.service.BackupService(database);
        settingsService = new SettingsService(new SettingsRepository(database), database);
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    // ------------------------------------------------------------- export

    @Test
    void exportNoteAsJson() throws Exception {
        noteService.createNote("JSON export", "nice content", null, Set.of("tag1"));
        Path target = tempDir.resolve("out.json");
        exportService.exportAllNotes("JSON", target);
        String json = FileUtil.readAll(target);
        assertTrue(json.contains("\"noteapp-export\""));
        assertTrue(json.contains("JSON export"));
    }

    @Test
    void exportNoteAsText() throws Exception {
        noteService.createNote("Text export", "plain and simple", null, Set.of());
        Path target = tempDir.resolve("out.txt");
        exportService.exportAllNotes("TXT", target);
        String text = FileUtil.readAll(target);
        assertTrue(text.contains("Text export"));
        assertTrue(text.contains("plain and simple"));
    }

    @Test
    void exporterPolymorphismAddsNewFormatWithoutCoreChanges() throws Exception {
        // Demonstrates runtime polymorphism: a brand-new format plugs in.
        exportService.registerExporter(new com.example.noteapp.export.Exporter() {
            @Override public String formatName() { return "MARKDOWN"; }
            @Override public String fileExtension() { return "md"; }
            @Override public void export(List<Note> notes, Path target) throws com.example.noteapp.exception.ExportException {
                try {
                    Files.writeString(target, "# notes");
                } catch (java.io.IOException e) {
                    throw new com.example.noteapp.exception.ExportException("test exporter failed", e);
                }
            }
        });
        try {
            Path target = tempDir.resolve("out.md");
            exportService.exportAllNotes("MARKDOWN", target);
            assertEquals("# notes", FileUtil.readAll(target));
        } catch (Exception e) {
            fail("Polymorphic exporter registration should work: " + e.getMessage());
        }
    }

    @Test
    void unknownExportFormatFailsGracefully() {
        assertThrows(com.example.noteapp.exception.ExportException.class,
                () -> exportService.exportAllNotes("XML", tempDir.resolve("x.xml")));
    }

    // ------------------------------------------------------------- import

    @Test
    void importValidJson() throws Exception {
        Path file = tempDir.resolve("in.json");
        Files.writeString(file, """
                {
                  "type": "noteapp-export",
                  "version": 1,
                  "notes": [
                    {"title": "Imported note", "content": "hello", "category": "FromImport",
                     "tags": ["one", "two"], "pinned": true}
                  ]
                }
                """);
        int count = importService.importNotes(file);
        assertEquals(1, count);
        List<Note> all = noteService.listActiveNotes();
        assertEquals(1, all.size());
        assertEquals("Imported note", all.get(0).getTitle());
        assertEquals(Set.of("one", "two"), all.get(0).getTags());
        assertTrue(all.get(0).isPinned());
        // category was auto-created
        assertTrue(categoryService.findByName("FromImport").isPresent());
    }

    @Test
    void roundTripExportThenImport() throws Exception {
        noteService.createNote("Round trip", "body", null, Set.of("rt"));
        Path export = tempDir.resolve("rt.json");
        exportService.exportAllNotes("JSON", export);

        // import into the same db is allowed; verify the note count grew
        int before = noteService.listActiveNotes().size();
        assertTrue(importService.importNotes(export) > 0);
        assertEquals(before + 1, noteService.listActiveNotes().size());
    }

    @Test
    void rejectInvalidJson() throws Exception {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "{ this is not json ");
        assertThrows(ImportException.class, () -> importService.importNotes(file));
    }

    @Test
    void rejectJsonWithoutNotesArray() throws Exception {
        Path file = tempDir.resolve("wrong.json");
        Files.writeString(file, "{\"something\": 1}");
        assertThrows(ImportException.class, () -> importService.importNotes(file));
    }

    @Test
    void rejectImportWithEmptyTitle() throws Exception {
        Path file = tempDir.resolve("empty-title.json");
        Files.writeString(file, "{\"notes\": [{\"title\": \"   \", \"content\": \"x\"}]}");
        assertThrows(ImportException.class, () -> importService.importNotes(file));
    }

    @Test
    void rejectTxtImport() throws Exception {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "some text");
        ImportException e = assertThrows(ImportException.class, () -> importService.importNotes(file));
        assertTrue(e.getMessage().contains("TXT"));
    }

    @Test
    void rejectMissingImportFile() {
        assertThrows(ImportException.class,
                () -> importService.importNotes(tempDir.resolve("missing.json")));
    }

    // ------------------------------------------------------------- backup / restore

    @Test
    void backupCreatesValidFolder() throws Exception {
        noteService.createNote("Backed up", "content", null, Set.of());
        Path backupRoot = tempDir.resolve("backups");
        Path backup = backupService.backup(backupRoot);
        assertTrue(backupService.isValidBackup(backup));
        assertTrue(Files.exists(backup.resolve("noteapp.db")));
    }

    @Test
    void restoreRejectsInvalidBackupFolder() throws Exception {
        Path notABackup = tempDir.resolve("empty");
        Files.createDirectories(notABackup);
        assertThrows(com.example.noteapp.exception.BackupException.class,
                () -> backupService.restore(notABackup));
    }

    // ------------------------------------------------------------- settings & PIN

    @Test
    void settingsRoundTrip() throws Exception {
        AppSettings settings = settingsService.getSettings();
        settings.setTheme(AppSettings.Theme.DARK);
        settings.setAutosaveDelaySeconds(7);
        settings.setDefaultCategory("Work");
        settingsService.saveSettings(settings);

        // a fresh service reads what was persisted
        SettingsService reloaded = new SettingsService(new SettingsRepository(database), database);
        assertEquals(AppSettings.Theme.DARK, reloaded.getSettings().getTheme());
        assertEquals(7, reloaded.getSettings().getAutosaveDelaySeconds());
        assertEquals("Work", reloaded.getSettings().getDefaultCategory());
    }

    @Test
    void pinHashIsStoredNotPlaintext() throws Exception {
        settingsService.enableLock("1234");
        AppSettings stored = new SettingsService(new SettingsRepository(database), database).getSettings();
        assertTrue(stored.isLockEnabled());
        assertNotEquals("1234", stored.getPinHash());
        assertFalse(stored.getPinHash().isBlank());
        assertNotNull(stored.getPinSalt());

        assertTrue(settingsService.verifyPin("1234"));
        assertFalse(settingsService.verifyPin("9999"));

        settingsService.disableLock();
        assertFalse(settingsService.isLockEnabled());
    }

    @Test
    void rejectInvalidPin() {
        assertThrows(Exception.class, () -> settingsService.enableLock("abc"));
        assertThrows(Exception.class, () -> settingsService.enableLock("123"));
        assertThrows(Exception.class, () -> settingsService.enableLock("123456789"));
        assertThrows(Exception.class, () -> settingsService.enableLock(""));
    }
}
