package com.notetaker.notes.v1.repository;

import com.notetaker.notes.v1.repository.entity.NoteEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<NoteEntity, String> {

  Page<NoteEntity> findByOwnerId(String ownerId, Pageable pageable);

  @Query("select n from com.notetaker.notes.v1.repository.entity.NoteEntity n where n.ownerId = :userId or n.id in (select s.noteId from com.notetaker.notes.v1.repository.entity.NoteShareEntity s where s.sharedWithUserId = :userId)")
  Page<NoteEntity> findVisibleToUser(@Param("userId") String userId, Pageable pageable);

  @Query("select n from com.notetaker.notes.v1.repository.entity.NoteEntity n where (n.ownerId = :userId or n.id in (select s.noteId from com.notetaker.notes.v1.repository.entity.NoteShareEntity s where s.sharedWithUserId = :userId)) and lower(n.title) like lower(concat('%', :title, '%'))")
  Page<NoteEntity> findVisibleToUserAndTitle(
      @Param("userId") String userId, @Param("title") String title, Pageable pageable);
}
