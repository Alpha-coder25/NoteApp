package com.example.noteapp.export;

import com.example.noteapp.exception.ExportException;
import com.example.noteapp.model.Note;
import com.example.noteapp.util.DateUtil;
import com.example.noteapp.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Exports notes as JSON (is-a {@link Exporter}). The emitted structure is the
 * same one {@code ImportService} accepts, so a JSON export can be re-imported.
 */
public class JsonExporter implements Exporter {

    private final LongFunction<String> categoryNameResolver;

    public JsonExporter(LongFunction<String> categoryNameResolver) {
        this.categoryNameResolver = categoryNameResolver;
    }

    @Override
    public String formatName() { return "JSON"; }

    @Override
    public String fileExtension() { return "json"; }

    @Override
    public void export(List<Note> notes, Path target) throws ExportException {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Note note : notes) {
            items.add(toMap(note));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "noteapp-export");
        root.put("version", 1);
        root.put("notes", items);
        try {
            Files.writeString(target, JsonUtil.write(root, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExportException("Unable to write the JSON file. Check the folder and try again.", e);
        }
    }

    /** Same structure the import accepts, so a JSON export round-trips. */
    Map<String, Object> toMap(Note note) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", note.getTitle());
        map.put("content", note.getContent());
        map.put("category", note.getCategoryId() == null ? null : categoryNameResolver.apply(note.getCategoryId()));
        map.put("tags", new ArrayList<>(note.getTags()));
        map.put("pinned", note.isPinned());
        map.put("archived", note.isArchived());
        map.put("created_at", DateUtil.toStorage(note.getCreatedAt()));
        map.put("updated_at", DateUtil.toStorage(note.getUpdatedAt()));
        return map;
    }
}
