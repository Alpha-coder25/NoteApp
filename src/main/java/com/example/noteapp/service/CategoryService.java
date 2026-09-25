package com.example.noteapp.service;

import com.example.noteapp.exception.DuplicateCategoryException;
import com.example.noteapp.exception.InvalidNoteException;
import com.example.noteapp.model.Category;
import com.example.noteapp.repository.CategoryRepository;
import com.example.noteapp.repository.NoteRepository;
import com.example.noteapp.util.Validator;

import java.util.List;
import java.util.Optional;

/**
 * Business rules for categories: create/rename/delete with duplicate
 * detection and protection for categories that still have notes assigned.
 */
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final NoteRepository noteRepository;

    // Composition: the service owns its collaborators.
    public CategoryService(CategoryRepository categoryRepository, NoteRepository noteRepository) {
        this.categoryRepository = categoryRepository;
        this.noteRepository = noteRepository;
    }

    public Category create(String name) throws InvalidNoteException, DuplicateCategoryException, com.example.noteapp.exception.DatabaseException {
        Validator.validateCategory(new Category(name));   // validates the name
        if (categoryRepository.findByName(name).isPresent()) {
            throw new DuplicateCategoryException(name.strip());
        }
        return categoryRepository.save(new Category(name));
    }

    public Category rename(long id, String newName) throws InvalidNoteException, DuplicateCategoryException, com.example.noteapp.exception.DatabaseException {
        Validator.validateCategory(new Category(newName));
        Optional<Category> existing = categoryRepository.findByName(newName);
        if (existing.isPresent() && existing.get().getId() != id) {
            throw new DuplicateCategoryException(newName.strip());
        }
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new InvalidNoteException("That category no longer exists."));
        category.setName(newName);
        return categoryRepository.save(category);
    }

    /**
     * Deletes a category. Notes keep their content but become uncategorized,
     * because the FK is {@code ON DELETE SET NULL}; the in-use check lets the
     * UI warn the user first.
     */
    public boolean delete(long id) throws com.example.noteapp.exception.DatabaseException {
        boolean inUse = noteRepository.countByCategory(id) > 0;
        categoryRepository.deleteById(id);
        return inUse;
    }

    public List<Category> list() throws com.example.noteapp.exception.DatabaseException {
        return categoryRepository.findAll();
    }

    /** Resolves a category name (case-insensitive) - used by import. */
    public Optional<Category> findByName(String name) throws com.example.noteapp.exception.DatabaseException {
        return name == null || name.isBlank() ? Optional.empty() : categoryRepository.findByName(name);
    }

    public Optional<Category> findById(long id) throws com.example.noteapp.exception.DatabaseException {
        return categoryRepository.findById(id);
    }

    /** Number of active notes in the category, for the sidebar. */
    public long countNotesIn(long categoryId) throws com.example.noteapp.exception.DatabaseException {
        return noteRepository.countByCategory(categoryId);
    }
}
