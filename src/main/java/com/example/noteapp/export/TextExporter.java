package com.example.noteapp.export;

import com.example.noteapp.exception.ExportException;
import com.example.noteapp.model.Note;
import com.example.noteapp.util.DateUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Exports notes as human-readable plain text (is-a {@link Exporter}).
 *
 * <p>Receives a category-name resolver via its constructor instead of a
 * repository dependency, keeping exporters decoupled from the data layer.
 */
public class TextExporter implements Exporter {

    private static final String SEPARATOR = "==================================================";

    private final LongFunction<String> categoryNameResolver;

    public TextExporter(LongFunction<String> categoryNameResolver) {
        this.categoryNameResolver = categoryNameResolver;
    }

    @Override
    public String formatName() { return "TXT"; }

    @Override
    public String fileExtension() { return "txt"; }

    @Override
    public void export(List<Note> notes, Path target) throws ExportException {
        StringBuilder sb = new StringBuilder();
        for (Note note : notes) {
            if (!sb.isEmpty()) {
                sb.append("\n\n").append(SEPARATOR).append("\n\n");
            }
            appendNote(sb, note);
        }
        try {
            Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExportException("Unable to write the text file. Check the folder and try again.", e);
        }
    }

    private void appendNote(StringBuilder sb, Note note) {
        sb.append(note.getTitle()).append('\n');
        sb.append(SEPARATOR).append('\n');
        if (note.getCategoryId() != null) {
            sb.append("Category: ").append(categoryNameResolver.apply(note.getCategoryId())).append('\n');
        }
        if (!note.getTags().isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", note.getTags())).append('\n');
        }
        sb.append("Created:  ").append(DateUtil.toUi(note.getCreatedAt())).append('\n');
        sb.append("Modified: ").append(DateUtil.toUi(note.getUpdatedAt())).append('\n');
        sb.append('\n').append(note.getContent()).append('\n');
    }
}
