package com.example.noteapp.repository;

import com.example.noteapp.exception.DatabaseException;

import java.util.List;
import java.util.Optional;

/**
 * Generic repository contract (abstraction + polymorphism).
 *
 * <p>Concrete repositories implement the CRUD core; service code can depend on
 * this interface instead of concrete classes. Kept intentionally small: extra
 * domain-specific queries live on the concrete repositories, not here.
 */
public interface Repository<T> {

    /** Inserts a new entity or updates an existing one; returns the persisted entity. */
    T save(T entity) throws DatabaseException;

    Optional<T> findById(long id) throws DatabaseException;

    List<T> findAll() throws DatabaseException;

    /** Hard-deletes the entity's row. Soft deletion is a service-level concern. */
    void delete(T entity) throws DatabaseException;
}
