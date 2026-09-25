package com.example.noteapp.util;

import com.example.noteapp.exception.DatabaseException;

import java.util.List;

/**
 * Abstraction for anything whose records can be filtered by free text.
 *
 * <p>Demonstrates <b>abstraction via interfaces</b>: {@code SearchService}
 * depends on this contract, not on a concrete repository, so the search
 * implementation can evolve (e.g. to full-text search) without touching callers.
 */
public interface Searchable {

    /**
     * Returns records matching the query (case-insensitive, matches title,
     * content, tags or category name).
     *
     * @param query raw user text; never {@code null}
     * @return matching records, possibly empty - never {@code null}
     */
    List<?> search(String query) throws DatabaseException;
}
