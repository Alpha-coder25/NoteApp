package com.example.noteapp.export;

import com.example.noteapp.exception.ExportException;
import com.example.noteapp.model.Note;

import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for note export formats (runtime polymorphism).
 *
 * <p>Depend on this interface, never on a concrete exporter. Adding a new
 * format (HTML, Markdown, ...) means adding one class and registering it -
 * no changes to {@code ExportService} or the UI.
 */
public interface Exporter {

    /** Short format identifier shown in the UI, e.g. "JSON". */
    String formatName();

    /** File extension without the dot, e.g. "json". */
    String fileExtension();

    /** Writes the given notes to {@code target}, overwriting it if present. */
    void export(List<Note> notes, Path target) throws ExportException;
}
