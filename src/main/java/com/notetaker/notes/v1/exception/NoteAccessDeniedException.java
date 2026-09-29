package com.notetaker.notes.v1.exception;

public class NoteAccessDeniedException extends RuntimeException {

  public NoteAccessDeniedException(String message) {
    super(message);
  }
}
