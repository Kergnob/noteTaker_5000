package com.notetaker.exception;

/** Thrown when the caller lacks permission for an action (mapped to HTTP 403). */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
