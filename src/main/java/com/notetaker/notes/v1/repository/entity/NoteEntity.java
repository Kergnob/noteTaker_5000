package com.notetaker.notes.v1.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "NOTE")
public class NoteEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "NOTE_ID")
  private Long id;

  /** Owning collaborative workspace; a note always lives in exactly one workspace. */
  @Column(name = "WORKSPACE_ID", nullable = false)
  private Long workspaceId;

  @Column(name = "TITLE_TXT", nullable = false, length = 300)
  private String title;

  @Lob
  @Column(name = "CONTENT_TXT")
  private String content;

  @Convert(converter = BooleanToCharConverter.class)
  @Column(name = "COMPLETED_FLG", nullable = false, columnDefinition = "CHAR(1)")
  private boolean completed;

  @Version
  @Column(name = "VERS_NBR", nullable = false)
  private Long version;

  /** app_user id of the creator. */
  @Column(name = "CRTD_BY_USER_ID", nullable = false)
  private Long createdByUserId;

  @Column(name = "REC_CRTN_TMSTP", nullable = false, updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "REC_MODIFY_TMSTP", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  /** When the note was moved to trash; null while active. */
  @Column(name = "DEL_TMSTP")
  private Instant deletedAt;
}
