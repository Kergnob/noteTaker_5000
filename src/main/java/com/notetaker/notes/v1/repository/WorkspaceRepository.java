package com.notetaker.notes.v1.repository;

import com.notetaker.notes.v1.repository.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, Long> {
}
