package com.example.noteapp.service;

import com.example.noteapp.exception.ImportException;
import com.example.noteapp.model.Note;
import com.example.noteapp.repository.NoteRepository;
import com.example.noteapp.util.DateUtil;
import com.example.noteapp.util.FileUtil;
import com.example.noteapp.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports notes from a JSON file (the format produced by {@code JsonExporter}).
 *
 * <p>Validation happens <b>before</b> anything is written: a file that is
 * missing, unreadable, structurally wrong or whose notes fail validation
 * results in zero database changes (all-or-nothing import).
 */
public class ImportService {

    private final NoteRepository noteRepository;
    private final CategoryService categoryService;

    public ImportService(NoteRepository noteRepository, CategoryService categoryService) {
        this.noteRepository = noteRepository;
        this.categoryService = categoryService;
    }

    /**
     * Imports notes from {@code file}; returns the number of notes imported.
     *
     * @throws ImportException when the file is invalid or its notes fail validation
     */
    public int importNotes(Path file) throws ImportException {
        String json = readFile(file);

        Object root;
        try {
            root = JsonUtil.parse(json);
        } catch (RuntimeException e) {
            throw new ImportException("The file is not valid JSON. Import cancelled.", e);
        }
        if (!(root instanceof Map)) {
            throw new ImportException("Unexpected file structure. Expected a JSON object with a \"notes\" array.");
        }

        Object notesNode = ((Map<?, ?>) root).get("notes");
        if (!(notesNode instanceof List)) {
            throw new ImportException("Missing or invalid \"notes\" array. Import cancelled.");
        }

        List<Note> parsed = new ArrayList<>();
        for (Object item : (List<?>) notesNode) {
            parsed.add(parseNote(item));
        }
        if (parsed.isEmpty()) {
            throw new ImportException("The file contains no notes.");
        }

        // Parsing and validation succeeded - now write, all-or-nothing.
        try {
            for (Note note : parsed) {
                Note saved = noteRepository.save(note);
                noteRepository.loadTags(saved);
            }
        } catch (Exception e) {
            throw new ImportException("The notes could not be saved to the database. Import cancelled.", e);
        }
        return parsed.size();
    }

    private String readFile(Path file) throws ImportException {
        try {
            if (file == null || !java.nio.file.Files.isRegularFile(file)) {
                throw new ImportException("The selected file does not exist.");
            }
            if (file.toAbsolutePath().toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                throw new ImportException("TXT files cannot be imported. Please choose a JSON export file.");
            }
            return FileUtil.readAll(file);
        } catch (IOException e) {
            throw new ImportException("The file could not be read. It may be locked by another program.", e);
        }
    }

    private Note parseNote(Object item) throws ImportException {
        if (!(item instanceof Map)) {
            throw new ImportException("A note entry is not a JSON object. Import cancelled.");
        }
        Map<?, ?> map = (Map<?, ?>) item;

        String title = str(map.get("title"));
        String content = str(map.get("content"));
        Note note;
        try {
            note = new Note(title, content);
        } catch (com.example.noteapp.exception.InvalidNoteException e) {
            throw new ImportException("A note has an invalid title: " + e.getMessage());
        }
        try {
            note.setTags(parseTags(map.get("tags")));
        } catch (com.example.noteapp.exception.InvalidNoteException e) {
            throw new ImportException("A note has an invalid tag: " + e.getMessage());
        }

        if (map.get("pinned") instanceof Boolean pinned) {
            note.setPinned(pinned);
        }
        if (map.get("archived") instanceof Boolean archived) {
            note.setArchived(archived);
        }
        note.setCreatedAt(parseInstant(map.get("created_at")));
        note.setUpdatedAt(parseInstant(map.get("updated_at")));
        note.setCategoryId(resolveCategory(map.get("category")));
        return note;
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseTags(Object tagsNode) throws ImportException {
        if (tagsNode == null) {
            return new LinkedHashSet<>();
        }
        if (!(tagsNode instanceof List)) {
            throw new ImportException("A note's \"tags\" must be an array. Import cancelled.");
        }
        Set<String> tags = new LinkedHashSet<>();
        for (Object tag : (List<Object>) tagsNode) {
            String name = str(tag);
            if (name.isEmpty()) {
                continue;
            }
            try {
                // Reuse the model's validation rules for every imported tag.
                new com.example.noteapp.model.Tag(name);
                tags.add(name.strip().toLowerCase());
            } catch (com.example.noteapp.exception.InvalidNoteException e) {
                throw new ImportException("A note has an invalid tag \"" + name + "\".");
            }
        }
        return tags;
    }

    private Long resolveCategory(Object categoryNode) {
        String name = str(categoryNode);
        if (name.isEmpty()) {
            return null;
        }
        // Import creates missing categories instead of failing the whole file.
        try {
            return categoryService.findByName(name)
                    .map(category -> category.getId())
                    .orElseGet(() -> {
                        try {
                            return categoryService.create(name).getId();
                        } catch (Exception e) {
                            return null; // uncategorized beats a failed import
                        }
                    });
        } catch (Exception e) {
            return null;
        }
    }

    private Instant parseInstant(Object node) {
        if (node instanceof String s && !s.isBlank()) {
            try {
                return Instant.parse(s);
            } catch (Exception ignored) {
                // fall through to now
            }
        }
        return Instant.now();
    }

    private static String str(Object node) {
        return node == null ? "" : String.valueOf(node).strip();
    }
}
