package com.notetaker.notes.v1.repository;

import com.notetaker.notes.v1.repository.entity.NoteShareEntity;
import com.notetaker.notes.v1.repository.entity.NoteShareId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteShareRepository extends JpaRepository<NoteShareEntity, NoteShareId> {

  List<NoteShareEntity> findByNoteId(String noteId);

  List<NoteShareEntity> findBySharedWithUserId(String sharedWithUserId);

  void deleteByNoteId(String noteId);
}
