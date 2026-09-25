package com.example.noteapp.repository;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.model.Tag;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite data access for tags. Tag rows are created lazily from note saves. */
public class TagRepository implements Repository<Tag> {

    private final DatabaseManager database;

    public TagRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Tag save(Tag tag) throws DatabaseException {
        String sql = tag.getId() == 0
                ? "INSERT INTO tags (name) VALUES (?)"
                : "UPDATE tags SET name = ? WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tag.getName());
            if (tag.getId() != 0) {
                ps.setLong(2, tag.getId());
            }
            ps.executeUpdate();
            if (tag.getId() == 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        tag.setId(keys.getLong(1));
                    }
                }
            }
            return tag;
        } catch (SQLException e) {
            throw new DatabaseException("Unable to save the tag.", e);
        }
    }

    @Override
    public Optional<Tag> findById(long id) throws DatabaseException {
        String sql = "SELECT id, name FROM tags WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(hydrate(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Unable to read the tag.", e);
        }
    }

    public Optional<Tag> findByName(String name) throws DatabaseException {
        String sql = "SELECT id, name FROM tags WHERE name = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, name == null ? "" : name.strip().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(hydrate(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Unable to look up the tag.", e);
        }
    }

    @Override
    public List<Tag> findAll() throws DatabaseException {
        String sql = "SELECT id, name FROM tags ORDER BY name";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Tag> tags = new ArrayList<>();
            while (rs.next()) {
                tags.add(hydrate(rs));
            }
            return tags;
        } catch (SQLException e) {
            throw new DatabaseException("Unable to read tags.", e);
        }
    }

    @Override
    public void delete(Tag tag) throws DatabaseException {
        String sql = "DELETE FROM tags WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, tag.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Unable to delete the tag.", e);
        }
    }

    /** All distinct tag names currently in use (for the sidebar and filter combo). */
    public List<String> findAllTagNames() throws DatabaseException {
        String sql = "SELECT name FROM tags ORDER BY name";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            return names;
        } catch (SQLException e) {
            throw new DatabaseException("Unable to read tags.", e);
        }
    }

    private Tag hydrate(ResultSet rs) throws SQLException, DatabaseException {
        try {
            return new Tag(rs.getLong("id"), rs.getString("name"));
        } catch (com.example.noteapp.exception.InvalidNoteException e) {
            throw new DatabaseException("A stored tag failed validation.", e);
        }
    }
}
