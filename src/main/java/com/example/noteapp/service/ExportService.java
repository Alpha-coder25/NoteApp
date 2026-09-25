package com.example.noteapp.service;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.exception.ExportException;
import com.example.noteapp.export.Exporter;
import com.example.noteapp.export.JsonExporter;
import com.example.noteapp.export.TextExporter;
import com.example.noteapp.model.Note;
import com.example.noteapp.repository.CategoryRepository;
import com.example.noteapp.repository.NoteQuery;
import com.example.noteapp.repository.NoteRepository;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Facade over the {@link Exporter} strategy family. The concrete exporter is
 * selected at runtime by format name (polymorphism in action): this class and
 * the UI never reference {@code TextExporter} or {@code JsonExporter} directly.
 *
 * <p>Adding a format later = one new {@code Exporter} implementation + one
 * {@code registerExporter} call. Nothing else changes.
 */
public class ExportService {

    private final NoteRepository noteRepository;
    private final CategoryRepository categoryRepository;
    private final Map<String, Exporter> exporters = new LinkedHashMap<>();

    public ExportService(NoteRepository noteRepository, CategoryRepository categoryRepository) {
        this.noteRepository = noteRepository;
        this.categoryRepository = categoryRepository;
        LongFunction<String> categoryName = this::categoryName;
        registerExporter(new JsonExporter(categoryName));
        registerExporter(new TextExporter(categoryName));
    }

    /** Registers an additional exporter (open/closed principle). */
    public void registerExporter(Exporter exporter) {
        exporters.put(exporter.formatName().toUpperCase(), exporter);
    }

    /** Format names for the export dialog's combo box, in registration order. */
    public List<String> availableFormats() {
        return List.copyOf(exporters.keySet());
    }

    /**
     * Exports the given notes using the named format.
     *
     * @throws ExportException when the format is unknown or writing fails
     */
    public void exportNotes(List<Note> notes, String format, Path target)
            throws ExportException, DatabaseException {
        Exporter exporter = exporters.get(format == null ? "" : format.toUpperCase());
        if (exporter == null) {
            throw new ExportException("Unknown export format: " + format);
        }
        exporter.export(notes, target);
    }

    /** Exports every active note. */
    public void exportAllNotes(String format, Path target) throws ExportException, DatabaseException {
        List<Note> notes = noteRepository.findBy(NoteQuery.active());
        noteRepository.loadTags(notes);
        exportNotes(notes, format, target);
    }

    private String categoryName(long categoryId) {
        try {
            return categoryRepository.findById(categoryId)
                    .map(category -> category.getName())
                    .orElse("Uncategorized");
        } catch (DatabaseException e) {
            // Export must never fail because a category name could not be resolved.
            return "Uncategorized";
        }
    }
}
