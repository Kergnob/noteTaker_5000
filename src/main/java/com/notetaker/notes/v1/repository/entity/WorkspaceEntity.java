package com.notetaker.notes.v1.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A collaborative workspace (a.k.a. space / group): notes inside it are shared amongst its members. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "WORKSPACE")
public class WorkspaceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "WORKSPACE_ID")
  private Long id;

  @Column(name = "WORKSPACE_NM", nullable = false, length = 200)
  private String name;

  @Column(name = "REC_CRTN_TMSTP", nullable = false, updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "REC_MODIFY_TMSTP", nullable = false)
  @UpdateTimestamp
  private Instant modifiedAt;
}
