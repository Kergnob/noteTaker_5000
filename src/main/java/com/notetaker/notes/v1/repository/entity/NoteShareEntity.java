package com.notetaker.notes.v1.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
@IdClass(NoteShareId.class)
@Table(name = "NOTE_SHARE")
public class NoteShareEntity {

  @Id
  @Column(name = "NOTE_ID", nullable = false, length = 36)
  private String noteId;

  @Id
  @Column(name = "SHARED_WITH_USER_ID", nullable = false, length = 50)
  private String sharedWithUserId;

  @Column(name = "PERMISSION", nullable = false, length = 10)
  private String permission;

  @Column(name = "REC_CRTN_TMSTP", nullable = false, updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  public NoteShareEntity(
      @NonNull String noteId, @NonNull String sharedWithUserId, @NonNull String permission) {
    this.noteId = noteId;
    this.sharedWithUserId = sharedWithUserId;
    this.permission = permission;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NoteShareEntity that = (NoteShareEntity) o;
    return Objects.equals(noteId, that.noteId)
        && Objects.equals(sharedWithUserId, that.sharedWithUserId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(noteId, sharedWithUserId);
  }
}
