package com.notetaker.security;

public class UserIdMissingException extends RuntimeException {

  public UserIdMissingException() {
    super("No user id claim present in the authentication token");
  }
}
