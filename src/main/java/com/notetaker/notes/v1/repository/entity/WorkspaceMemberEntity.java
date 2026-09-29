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
import lombok.NonNull;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Membership of a user in a workspace, carrying their role (OWNER / EDITOR / VIEWER). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "WORKSPACE_MEMBER")
public class WorkspaceMemberEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "WORKSPACE_MEMBER_ID")
  private Long id;

  @Column(name = "WORKSPACE_ID", nullable = false)
  private Long workspaceId;

  @Column(name = "USER_ID", nullable = false)
  private Long userId;

  @Column(name = "ROLE_CD", nullable = false, length = 10)
  private String role;

  @Column(name = "REC_CRTN_TMSTP", nullable = false, updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  public WorkspaceMemberEntity(@NonNull Long workspaceId, @NonNull Long userId, @NonNull String role) {
    this.workspaceId = workspaceId;
    this.userId = userId;
    this.role = role;
  }
}
