package com.example.noteapp.service;

import com.example.noteapp.exception.DatabaseException;
import com.example.noteapp.model.Tag;
import com.example.noteapp.repository.TagRepository;

import java.util.List;

/** Read-mostly tag logic: tags are created lazily from note saves. */
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /** All tag names currently in use, sorted alphabetically. */
    public List<String> listTagNames() throws DatabaseException {
        return tagRepository.findAllTagNames();
    }

    /** Finds a tag by exact (case-insensitive) name. */
    public java.util.Optional<Tag> findByName(String name) throws DatabaseException {
        return tagRepository.findByName(name);
    }
}
