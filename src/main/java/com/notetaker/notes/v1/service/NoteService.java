package com.notetaker.notes.v1.service;

import com.notetaker.notes.v1.repository.entity.NoteEntity;
import com.notetaker.notes.v1.repository.entity.NoteShareEntity;
import java.util.List;
import org.springframework.data.domain.Page;

public interface NoteService {

  NoteEntity create(String title, String content);

  NoteEntity get(String id);

  Page<NoteEntity> list(int pageNumber, int itemsPerPage, String searchTerm);

  NoteEntity update(String id, String title, String content, Boolean completed);

  NoteEntity complete(String id);

  void delete(String id);

  NoteShareEntity share(String id, String sharedWithUserId, String permission);

  List<NoteShareEntity> listShares(String id);

  void unshare(String id, String userId);
}
