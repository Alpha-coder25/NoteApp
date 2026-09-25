package com.example.noteapp.repository;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.model.Note;
import com.example.noteapp.util.DateUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite data access for notes. All SQL uses {@link PreparedStatement} and
 * every write runs inside a transaction managed by {@link DatabaseManager}.
 *
 * <p>Timestamps are stored as ISO-8601 strings, which keeps them comparable
 * in ORDER BY clauses.
 */
public class NoteRepository implements Repository<Note> {

    private final DatabaseManager database;

    public NoteRepository(DatabaseManager database) {
        this.database = database;
    }

    // ------------------------------------------------------------------ CRUD

    @Override
    public Note save(Note note) throws DatabaseException {
        return database.inTransaction(conn -> note.isNew() ? insert(conn, note) : update(conn, note));
    }

    /** Hard-deletes a note's row (tag links disappear via ON DELETE CASCADE). */
    @Override
    public void delete(Note note) throws DatabaseException {
        hardDelete(note.getId());
    }

    private Note insert(Connection conn, Note note) throws SQLException {
        Instant now = Instant.now();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO notes (title, content, category_id, created_at, updated_at,
                                   is_pinned, is_archived, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            bindNote(ps, note, now, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    note.setId(keys.getLong(1));
                }
            }
            syncTags(conn, note);
            return note;
        }
    }

    private Note update(Connection conn, Note note) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE notes SET title = ?, content = ?, category_id = ?, updated_at = ?,
                                 is_pinned = ?, is_archived = ?, is_deleted = ?
                WHERE id = ?
                """)) {
            ps.setString(1, note.getTitle());
            ps.setString(2, note.getContent());
            if (note.getCategoryId() == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, note.getCategoryId());
            }
            ps.setString(4, DateUtil.toStorage(Instant.now()));
            ps.setInt(5, note.isPinned() ? 1 : 0);
            ps.setInt(6, note.isArchived() ? 1 : 0);
            ps.setInt(7, note.isDeleted() ? 1 : 0);
            ps.setLong(8, note.getId());
            ps.executeUpdate();
            syncTags(conn, note);
            return note;
        }
    }

    @Override
    public Optional<Note> findById(long id) throws DatabaseException {
        String sql = """
                SELECT n.id, n.title, n.content, n.category_id, n.created_at, n.updated_at,
                       n.is_pinned, n.is_archived, n.is_deleted
                FROM notes n WHERE n.id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(hydrate(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to read note " + id + " from the database.", e);
        } catch (com.example.noteapp.exception.InvalidNoteException e) {
            // A row that fails validation means external tampering; surface it as a db error.
            throw new DatabaseException("A stored note failed validation.", e);
        }
    }

    @Override
    public List<Note> findAll() throws DatabaseException {
        return findBy(NoteQuery.active());
    }

    // ------------------------------------------------------------------ queries

    /** Lists notes matching the query; text search covers title, content, category and tags. */
    public List<Note> findBy(NoteQuery query) throws DatabaseException {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT n.id, n.title, n.content, n.category_id, n.created_at, n.updated_at,
                       n.is_pinned, n.is_archived, n.is_deleted
                FROM notes n
                LEFT JOIN categories c ON c.id = n.category_id
                """);
        if (query.tag() != null) {
            sql.append("""
                    LEFT JOIN note_tags nt ON nt.note_id = n.id
                    LEFT JOIN tags t ON t.id = nt.tag_id
                    """);
        }
        sql.append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (query.deleted() != null) {
            sql.append(" AND n.is_deleted = ?");
            params.add(query.deleted() ? 1 : 0);
        }
        if (query.archived() != null) {
            sql.append(" AND n.is_archived = ?");
            params.add(query.archived() ? 1 : 0);
        }
        if (query.pinned() != null) {
            sql.append(" AND n.is_pinned = ?");
            params.add(query.pinned() ? 1 : 0);
        }
        if (query.categoryId() != null) {
            sql.append(" AND n.category_id = ?");
            params.add(query.categoryId());
        }
        if (query.tag() != null) {
            sql.append(" AND t.name = ?");
            params.add(query.tag());
        }
        if (query.text() != null) {
            // ESCAPE '\' makes escaped % and _ match literally (e.g. searching "100%").
            sql.append("""
                    AND (LOWER(n.title) LIKE ? ESCAPE '\\' OR LOWER(n.content) LIKE ? ESCAPE '\\'
                         OR LOWER(COALESCE(c.name, '')) LIKE ? ESCAPE '\\'""");
            String like = "%" + escapeLike(query.text()) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            if (query.tag() == null) {
                // when not joined above, search tags via EXISTS
                sql.append(" OR EXISTS (SELECT 1 FROM note_tags nt2 JOIN tags t2 ON t2.id = nt2.tag_id\n"
                        + "             WHERE nt2.note_id = n.id AND LOWER(t2.name) LIKE ? ESCAPE '\\')");
                params.add(like);
            }
            sql.append(")");
        }

        // Pinning only reorders the default "recently modified" listing;
        // an explicit user sort (e.g. Title A-Z) wins outright.
        if (query.sortOrder() == SortOrder.UPDATED_DESC) {
            sql.append(" ORDER BY n.is_pinned DESC, ").append(query.sortOrder().toSql());
        } else {
            sql.append(" ORDER BY ").append(query.sortOrder().toSql());
        }

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Note> notes = new ArrayList<>();
                while (rs.next()) {
                    notes.add(hydrate(rs));
                }
                return notes;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query notes from the database.", e);
        } catch (com.example.noteapp.exception.InvalidNoteException e) {
            throw new DatabaseException("A stored note failed validation.", e);
        }
    }

    /** True when a row with this id exists (used before updates). */
    public boolean existsById(long id) throws DatabaseException {
        String sql = "SELECT 1 FROM notes WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to check the note's existence.", e);
        }
    }

    /** Number of notes currently in the trash (for the sidebar badge). */
    public long countDeleted() throws DatabaseException {
        return countWhere("is_deleted = 1");
    }

    /** Number of non-deleted notes in the given category. */
    public long countByCategory(long categoryId) throws DatabaseException {
        String sql = "SELECT COUNT(*) FROM notes WHERE category_id = ? AND is_deleted = 0";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count notes in the category.", e);
        }
    }

    private long countWhere(String condition) throws DatabaseException {
        String sql = "SELECT COUNT(*) FROM notes WHERE " + condition;
        // condition is a compile-time constant within this class, never user input
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count notes.", e);
        }
    }

    /** Permanently removes one note row (tag links vanish via ON DELETE CASCADE). */
    public void hardDelete(long id) throws DatabaseException {
        String sql = "DELETE FROM notes WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("The note could not be deleted permanently.", e);
        }
    }

    /** Permanently removes all trashed notes; returns how many rows were removed. */
    public int hardDeleteAllDeleted() throws DatabaseException {
        String sql = "DELETE FROM notes WHERE is_deleted = 1";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("The trash could not be emptied.", e);
        }
    }

    // ------------------------------------------------------------------ tags

    /** Replaces the note's tag links, creating missing tag rows (inside the save transaction). */
    private void syncTags(Connection conn, Note note) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM note_tags WHERE note_id = ?");
             PreparedStatement findTag = conn.prepareStatement("SELECT id FROM tags WHERE name = ?");
             PreparedStatement insertTag = conn.prepareStatement("INSERT INTO tags (name) VALUES (?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement link = conn.prepareStatement(
                     "INSERT OR IGNORE INTO note_tags (note_id, tag_id) VALUES (?, ?)")) {
            delete.setLong(1, note.getId());
            delete.executeUpdate();

            for (String tagName : note.getTags()) {
                long tagId;
                findTag.setString(1, tagName);
                try (ResultSet rs = findTag.executeQuery()) {
                    tagId = rs.next() ? rs.getLong(1) : -1;
                }
                if (tagId < 0) {
                    insertTag.setString(1, tagName);
                    insertTag.executeUpdate();
                    try (ResultSet keys = insertTag.getGeneratedKeys()) {
                        tagId = keys.next() ? keys.getLong(1) : -1;
                    }
                }
                link.setLong(1, note.getId());
                link.setLong(2, tagId);
                link.executeUpdate();
            }
        }
    }

    /** Loads tag names for one note (kept out of list queries to avoid N+1 in the row mapper). */
    public void loadTags(Note note) throws DatabaseException {
        String sql = """
                SELECT t.name FROM tags t
                JOIN note_tags nt ON nt.tag_id = t.id
                WHERE nt.note_id = ?
                ORDER BY t.name
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, note.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        note.addTag(rs.getString(1));
                    } catch (com.example.noteapp.exception.InvalidNoteException ignored) {
                        // tag row was already validated on write; never happens for stored data
                    }
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to read the note's tags.", e);
        }
    }

    /** Loads tags for a whole page of notes in a single query (avoids N+1). */
    public void loadTags(List<Note> notes) throws DatabaseException {
        if (notes.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("""
                SELECT nt.note_id, t.name FROM note_tags nt
                JOIN tags t ON t.id = nt.tag_id
                WHERE nt.note_id IN (
                """);
        for (int i = 0; i < notes.size(); i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(") ORDER BY t.name");
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < notes.size(); i++) {
                ps.setLong(i + 1, notes.get(i).getId());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long noteId = rs.getLong(1);
                    String tagName = rs.getString(2);
                    for (Note note : notes) {
                        if (note.getId() == noteId) {
                            try {
                                note.addTag(tagName);
                            } catch (com.example.noteapp.exception.InvalidNoteException ignored) {
                                // validated on write
                            }
                            break;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to read note tags.", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void bindNote(PreparedStatement ps, Note note, Instant created, Instant updated) throws SQLException {
        ps.setString(1, note.getTitle());
        ps.setString(2, note.getContent());
        if (note.getCategoryId() == null) {
            ps.setNull(3, java.sql.Types.BIGINT);
        } else {
            ps.setLong(3, note.getCategoryId());
        }
        ps.setString(4, DateUtil.toStorage(created));
        ps.setString(5, DateUtil.toStorage(updated));
        ps.setInt(6, note.isPinned() ? 1 : 0);
        ps.setInt(7, note.isArchived() ? 1 : 0);
        ps.setInt(8, note.isDeleted() ? 1 : 0);
    }

    private Note hydrate(ResultSet rs) throws SQLException, com.example.noteapp.exception.InvalidNoteException {
        return new Note(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getObject("category_id") == null ? null : rs.getLong("category_id"),
                parseTimestamp(rs.getString("created_at")),
                parseTimestamp(rs.getString("updated_at")),
                rs.getInt("is_pinned") == 1,
                rs.getInt("is_archived") == 1,
                rs.getInt("is_deleted") == 1);
    }

    /** Parses both ISO instant strings and legacy "yyyy-MM-dd HH:mm:ss" values. */
    private static Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            try {
                return Timestamp.valueOf(value).toInstant();
            } catch (Exception alsoIgnored) {
                return Instant.now();
            }
        }
    }

    /** Escapes LIKE wildcards in user text so "%"/"_" search literally. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
