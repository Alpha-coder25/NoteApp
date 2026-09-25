package com.example.noteapp.exception;

/**
 * Wraps low-level {@link java.sql.SQLException} failures so higher layers never
 * depend on JDBC directly. The SQL error stays attached as the cause for logging.
 */
public class DatabaseException extends AppException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
