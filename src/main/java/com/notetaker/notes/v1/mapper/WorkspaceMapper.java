package com.notetaker.notes.v1.mapper;

import com.notetaker.model.Workspace;
import com.notetaker.model.WorkspaceMember;
import com.notetaker.model.WorkspaceRole;
import com.notetaker.notes.v1.repository.entity.AppUserEntity;
import com.notetaker.notes.v1.repository.entity.WorkspaceEntity;
import java.time.Instant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

  default Workspace toModel(WorkspaceEntity entity, String role) {
    if (entity == null) {
      return null;
    }

    return Workspace.builder()
        .id(entity.getId())
        .name(entity.getName())
        .role(role == null ? null : WorkspaceRole.fromValue(role))
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getModifiedAt())
        .build();
  }

  default WorkspaceMember toModel(AppUserEntity user, String role, Instant joinedAt) {
    if (user == null) {
      return null;
    }

    return WorkspaceMember.builder()
        .userId(user.getId())
        .employeeNumber(user.getEmployeeNumber())
        .displayName(user.getDisplayName())
        .role(role == null ? null : WorkspaceRole.fromValue(role))
        .joinedAt(joinedAt)
        .build();
  }
}
