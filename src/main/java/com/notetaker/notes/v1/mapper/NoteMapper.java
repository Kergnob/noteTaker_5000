package com.notetaker.notes.v1.mapper;

import com.notetaker.model.Note;
import com.notetaker.notes.v1.repository.entity.NoteEntity;
import org.mapstruct.Mapper;
import org.openapitools.jackson.nullable.JsonNullable;

@Mapper(componentModel = "spring")
public interface NoteMapper {

  default Note toModel(NoteEntity entity) {
    if (entity == null) {
      return null;
    }

    return Note.builder()
        .id(entity.getId())
        .workspaceId(entity.getWorkspaceId())
        .title(entity.getTitle())
        .content(entity.getContent())
        .completed(entity.isCompleted())
        .createdByUserId(entity.getCreatedByUserId())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .deletedAt(entity.getDeletedAt() == null
            ? JsonNullable.undefined()
            : JsonNullable.of(entity.getDeletedAt()))
        .build();
  }
}
