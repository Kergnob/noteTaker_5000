package com.notetaker.notes.v1.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
  @Column(name = "NOTE_ID", nullable = false, length = 36)
  private String id;

  @Column(name = "OWNER_ID", nullable = false, length = 50)
  private String ownerId;

  @Column(name = "TITLE", nullable = false, length = 200)
  private String title;

  @Lob
  @Column(name = "CONTENT", length = 10_485_760)
  private String content;

  @Column(name = "COMPLETED_FLG", nullable = false)
  private boolean completed;

  @Column(name = "REC_CRTN_TMSTP", nullable = false, updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "REC_UPDT_TMSTP", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  @Version
  @Column(name = "VERSION")
  private Long version;
}
