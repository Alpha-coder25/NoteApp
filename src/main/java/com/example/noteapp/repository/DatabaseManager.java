package com.example.noteapp.repository;

import com.example.noteapp.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection, creates the schema on first launch and
 * provides transaction support.
 *
 * <p>Data location: {@code %APPDATA%\NoteApp\noteapp.db} on Windows (user-
 * specific, never inside the installation directory). Tests and power users can
 * override it with the {@code NOTEAPP_DATA_DIR} environment variable.
 *
 * <p>This is the <b>only</b> class in the app that talks to JDBC directly;
 * repositories receive the connection from here (composition, no globals).
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);

    private final Path databaseFile;
    private Connection connection;

    public DatabaseManager() throws DatabaseException {
        this(defaultDataDirectory());
    }

    public DatabaseManager(Path dataDirectory) throws DatabaseException {
        this.databaseFile = dataDirectory.resolve("noteapp.db");
        try {
            Files.createDirectories(dataDirectory);
        } catch (java.io.IOException e) {
            throw new DatabaseException("Unable to create the application data folder. "
                    + "Check that your user profile is writable.", e);
        }
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");   // crash-safe, faster commits
            }
            createSchema();
            LOG.info("Database initialized at {}", databaseFile);
        } catch (SQLException e) {
            throw new DatabaseException("Unable to open the local database. "
                    + "Check that the app folder is writable.", e);
        } catch (ClassNotFoundException e) {
            throw new DatabaseException("SQLite driver is missing from the application.", e);
        }
    }

    /** The single database file, exposed for backup/restore. */
    public Path getDatabaseFile() { return databaseFile; }

    public Connection getConnection() { return connection; }

    /**
     * Runs {@code work} inside a transaction: commits on success, rolls back on
     * any failure and rethrows as {@link DatabaseException}. Used for all
     * multi-step writes (save note + tags, restore, ...) so data is never left
     * half-written.
     */
    public <T> T inTransaction(SqlWork<T> work) throws DatabaseException {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception failure) {
                connection.rollback();
                if (failure instanceof DatabaseException dbFailure) {
                    throw dbFailure;
                }
                throw new DatabaseException("Database operation failed and was rolled back.", failure);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Unable to manage the database transaction.", e);
        }
    }

    /** Work unit executed inside {@link #inTransaction}. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection connection) throws Exception;
    }

    /** Creates all tables and indexes if they do not exist yet (idempotent). */
    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id   INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS notes (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        title       TEXT NOT NULL,
                        content     TEXT NOT NULL DEFAULT '',
                        category_id INTEGER,
                        created_at  TEXT NOT NULL,
                        updated_at  TEXT NOT NULL,
                        is_pinned   INTEGER NOT NULL DEFAULT 0,
                        is_archived INTEGER NOT NULL DEFAULT 0,
                        is_deleted  INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id   INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS note_tags (
                        note_id INTEGER NOT NULL,
                        tag_id  INTEGER NOT NULL,
                        PRIMARY KEY (note_id, tag_id),
                        FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,
                        FOREIGN KEY (tag_id)  REFERENCES tags(id)  ON DELETE CASCADE
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key   TEXT PRIMARY KEY,
                        value TEXT
                    )""");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notes_updated  ON notes(updated_at)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notes_deleted  ON notes(is_deleted)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notes_archived ON notes(is_archived)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notes_category ON notes(category_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_note_tags_tag  ON note_tags(tag_id)");
        }
    }

    /** Closes the connection; called on app shutdown and between backup steps. */
    @Override
    public void close() throws DatabaseException {
        if (connection != null) {
            try {
                connection.close();
                LOG.info("Database connection closed");
            } catch (SQLException e) {
                throw new DatabaseException("Unable to close the database cleanly.", e);
            }
        }
    }

    /**
     * Resolves the per-user data directory.
     * Order: {@code NOTEAPP_DATA_DIR} env var, then {@code %APPDATA%\NoteApp},
     * then {@code ~/.noteapp} as a portable fallback on other systems.
     */
    public static Path defaultDataDirectory() {
        String override = System.getenv("NOTEAPP_DATA_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override.strip());
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "NoteApp");
        }
        return Path.of(System.getProperty("user.home"), ".noteapp");
    }
}
