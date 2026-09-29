package com.notetaker.notes.v1.repository.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class NoteShareId implements Serializable {
  private static final long serialVersionUID = 1L;

  private String noteId;
  private String sharedWithUserId;
}
