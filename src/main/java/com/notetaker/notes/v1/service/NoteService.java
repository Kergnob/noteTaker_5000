package com.notetaker.notes.v1.service;

import com.notetaker.notes.v1.repository.entity.NoteEntity;
import org.springframework.data.domain.Page;

public interface NoteService {

  NoteEntity create(Long workspaceId, String title, String content);

  NoteEntity get(Long id);

  Page<NoteEntity> list(Long workspaceId, int pageNumber, int itemsPerPage, String searchTerm);

  Page<NoteEntity> listTrash(Long workspaceId, int pageNumber, int itemsPerPage);

  NoteEntity update(Long id, String title, String content, Boolean completed);

  NoteEntity complete(Long id);

  NoteEntity restore(Long id);

  void delete(Long id);
}
