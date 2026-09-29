package com.notetaker.notes.v1.repository;

import com.notetaker.notes.v1.repository.entity.NoteEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<NoteEntity, Long> {

  // A note is visible to a user when it lives in a workspace they belong to. Optional
  // workspaceId / title filters are applied only when non-null.
  String VISIBLE =
      " n.workspaceId in (select m.workspaceId from"
          + " com.notetaker.notes.v1.repository.entity.WorkspaceMemberEntity m where m.userId = :userId)"
          + " and (:workspaceId is null or n.workspaceId = :workspaceId)";

  @Query("select n from com.notetaker.notes.v1.repository.entity.NoteEntity n"
      + " where n.deletedAt is null and" + VISIBLE
      + " and (:title is null or lower(n.title) like lower(concat('%', :title, '%')))")
  Page<NoteEntity> findActiveVisible(
      @Param("userId") Long userId,
      @Param("workspaceId") Long workspaceId,
      @Param("title") String title,
      Pageable pageable);

  @Query("select n from com.notetaker.notes.v1.repository.entity.NoteEntity n"
      + " where n.deletedAt is not null and" + VISIBLE)
  Page<NoteEntity> findTrashedVisible(
      @Param("userId") Long userId,
      @Param("workspaceId") Long workspaceId,
      Pageable pageable);
}
