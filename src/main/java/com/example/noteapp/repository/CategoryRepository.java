package com.example.noteapp.repository;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.model.Category;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite data access for categories. Names are unique (case-insensitive). */
public class CategoryRepository implements Repository<Category> {

    private final DatabaseManager database;

    public CategoryRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Category save(Category category) throws DatabaseException {
        String sql = category.getId() == 0
                ? "INSERT INTO categories (name) VALUES (?)"
                : "UPDATE categories SET name = ? WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            if (category.getId() != 0) {
                ps.setLong(2, category.getId());
            }
            ps.executeUpdate();
            if (category.getId() == 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        category.setId(keys.getLong(1));
                    }
                }
            }
            return category;
        } catch (SQLException e) {
            throw new DatabaseException("Unable to save the category.", e);
        }
    }

    @Override
    public Optional<Category> findById(long id) throws DatabaseException {
        String sql = "SELECT id, name FROM categories WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(hydrate(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Unable to read the category.", e);
        }
    }

    /** Case-insensitive lookup used to detect duplicates. */
    public Optional<Category> findByName(String name) throws DatabaseException {
        String sql = "SELECT id, name FROM categories WHERE name = ? COLLATE NOCASE";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, name == null ? "" : name.strip());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(hydrate(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Unable to look up the category.", e);
        }
    }

    @Override
    public List<Category> findAll() throws DatabaseException {
        String sql = "SELECT id, name FROM categories ORDER BY name COLLATE NOCASE";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Category> categories = new ArrayList<>();
            while (rs.next()) {
                categories.add(hydrate(rs));
            }
            return categories;
        } catch (SQLException e) {
            throw new DatabaseException("Unable to read categories.", e);
        }
    }

    @Override
    public void delete(Category category) throws DatabaseException {
        deleteById(category.getId());
    }

    /** Deletes by id (notes referencing it become uncategorized via ON DELETE SET NULL). */
    public void deleteById(long id) throws DatabaseException {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Unable to delete the category.", e);
        }
    }

    private Category hydrate(ResultSet rs) throws SQLException, DatabaseException {
        try {
            return new Category(rs.getLong("id"), rs.getString("name"));
        } catch (com.example.noteapp.exception.InvalidNoteException e) {
            throw new DatabaseException("A stored category failed validation.", e);
        }
    }
}
