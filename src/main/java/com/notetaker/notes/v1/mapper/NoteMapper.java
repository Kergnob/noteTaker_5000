package com.notetaker.notes.v1.mapper;

import com.notetaker.model.Note;
import com.notetaker.model.NoteShare;
import com.notetaker.model.SharePermission;
import com.notetaker.notes.v1.repository.entity.NoteEntity;
import com.notetaker.notes.v1.repository.entity.NoteShareEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NoteMapper {

  default Note toModel(NoteEntity entity) {
    if (entity == null) {
      return null;
    }

    return Note.builder()
        .id(entity.getId())
        .ownerId(entity.getOwnerId())
        .title(entity.getTitle())
        .content(entity.getContent())
        .completed(entity.isCompleted())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  default NoteShare toModel(NoteShareEntity entity) {
    if (entity == null) {
      return null;
    }

    return NoteShare.builder()
        .noteId(entity.getNoteId())
        .sharedWithUserId(entity.getSharedWithUserId())
        .permission(map(entity.getPermission()))
        .createdAt(entity.getCreatedAt())
        .build();
  }

  default SharePermission map(String permission) {
    return permission == null ? null : SharePermission.fromValue(permission);
  }
}
