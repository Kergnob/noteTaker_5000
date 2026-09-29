package com.notetaker.notes.v1.service;

import java.util.Set;

/** The roles a user can hold within a workspace, ordered from most to least privileged. */
public final class WorkspaceRole {

  public static final String OWNER = "OWNER";
  public static final String EDITOR = "EDITOR";
  public static final String VIEWER = "VIEWER";

  public static final Set<String> ALL = Set.of(OWNER, EDITOR, VIEWER);
  /** Roles allowed to create/modify/delete notes in the workspace. */
  public static final Set<String> CAN_EDIT = Set.of(OWNER, EDITOR);

  private WorkspaceRole() {}

  public static boolean isValid(String role) {
    return role != null && ALL.contains(role);
  }
}
