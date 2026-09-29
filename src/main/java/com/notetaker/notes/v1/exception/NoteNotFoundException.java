package com.notetaker.notes.v1.exception;

public class NoteNotFoundException extends RuntimeException {

  public NoteNotFoundException(String id) {
    super("Note " + id + " not found");
  }
}
