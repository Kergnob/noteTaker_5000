package com.notetaker.notes.v1.repository;

import com.notetaker.notes.v1.repository.entity.WorkspaceMemberEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, Long> {

  List<WorkspaceMemberEntity> findByWorkspaceId(Long workspaceId);

  List<WorkspaceMemberEntity> findByUserId(Long userId);

  Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
