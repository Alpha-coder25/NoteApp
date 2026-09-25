package com.example.noteapp.model;

import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Model and utility behavior that does not need a database. */
class NoteValidationTest {

    @Test
    void newNoteHasTimestamps() throws Exception {
        Note note = new Note("Title", "Body");
        assertNotNull(note.getCreatedAt());
        assertNotNull(note.getUpdatedAt());
        assertTrue(note.isNew());
    }

    @Test
    void titleIsTrimmed() throws Exception {
        Note note = new Note("  padded  ", "");
        assertEquals("padded", note.getTitle());
    }

    @Test
    void nullContentBecomesEmpty() throws Exception {
        Note note = new Note("Title", null);
        assertEquals("", note.getContent());
    }

    @Test
    void tagsAreNormalizedToLowercase() throws Exception {
        Note note = new Note("Title", "");
        note.addTag("  Java ");
        assertEquals(Set.of("java"), note.getTags());
    }

    @Test
    void duplicateTagsCollapse() throws Exception {
        Note note = new Note("Title", "");
        note.addTag("same");
        note.addTag("SAME");
        assertEquals(1, note.getTags().size());
    }

    @Test
    void tagsViewIsUnmodifiable() throws Exception {
        Note note = new Note("Title", "");
        assertThrows(UnsupportedOperationException.class, () -> note.getTags().add("hack"));
    }

    @Test
    void equalityIsByIdNotContent() throws Exception {
        Note a = new Note(1, "A", "one", null, null, null, false, false, false);
        Note b = new Note(1, "B", "two", null, null, null, false, false, false);
        assertEquals(a, b);
        Note unsaved = new Note("unsaved", "");
        assertNotEquals(a, unsaved);
    }

    // ------------------------------------------------------------- JSON utility

    @Test
    void jsonRoundTrip() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", "Quote \" and \\ slash");
        map.put("count", 3);
        map.put("pi", 1.5);
        map.put("ok", true);
        map.put("nothing", null);
        map.put("tags", java.util.List.of("a", "b"));

        String json = JsonUtil.write(map);
        Object parsed = JsonUtil.parse(json);
        assertTrue(parsed instanceof Map);
        Map<?, ?> back = (Map<?, ?>) parsed;
        assertEquals("Quote \" and \\ slash", back.get("title"));
        assertEquals(3L, back.get("count"));
        assertEquals(Boolean.TRUE, back.get("ok"));
        assertNull(back.get("nothing"));
        assertEquals(java.util.List.of("a", "b"), back.get("tags"));
    }

    @Test
    void jsonRejectsGarbage() {
        assertThrows(RuntimeException.class, () -> JsonUtil.parse("{broken"));
        assertThrows(RuntimeException.class, () -> JsonUtil.parse("[1, 2"));
        assertThrows(RuntimeException.class, () -> JsonUtil.parse("\"unterminated"));
        assertThrows(RuntimeException.class, () -> JsonUtil.parse(""));
    }

    @Test
    void jsonHandlesUnicodeEscapes() {
        Object parsed = JsonUtil.parse("\"\\u00e9l\\u00e8ve\"");
        assertEquals("élève", parsed);
    }
}
