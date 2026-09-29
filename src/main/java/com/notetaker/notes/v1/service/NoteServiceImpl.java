package com.notetaker.notes.v1.service;

import com.notetaker.exception.ForbiddenException;
import com.notetaker.exception.NotFoundException;
import com.notetaker.notes.v1.repository.NoteRepository;
import com.notetaker.notes.v1.repository.WorkspaceMemberRepository;
import com.notetaker.notes.v1.repository.entity.NoteEntity;
import com.notetaker.notes.v1.repository.entity.WorkspaceMemberEntity;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class NoteServiceImpl implements NoteService {

  private final NoteRepository noteRepository;
  private final WorkspaceMemberRepository memberRepository;
  private final UserService userService;

  @Override
  public NoteEntity create(Long workspaceId, String title, String content) {
    Long userId = currentUserId();
    requireEdit(workspaceId, userId);
    NoteEntity note = new NoteEntity();
    note.setWorkspaceId(workspaceId);
    note.setCreatedByUserId(userId);
    note.setTitle(title);
    note.setContent(content);
    note.setCompleted(false);
    return noteRepository.save(note);
  }

  @Override
  @Transactional(readOnly = true)
  public NoteEntity get(Long id) {
    NoteEntity note = find(id);
    requireMember(note.getWorkspaceId(), currentUserId());
    return note;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<NoteEntity> list(Long workspaceId, int pageNumber, int itemsPerPage, String searchTerm) {
    PageRequest pageable = PageRequest.of(
        pageNumber - 1, itemsPerPage, Sort.by(Sort.Direction.DESC, "updatedAt"));
    String title = StringUtils.hasText(searchTerm) ? searchTerm : null;
    return noteRepository.findActiveVisible(currentUserId(), workspaceId, title, pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<NoteEntity> listTrash(Long workspaceId, int pageNumber, int itemsPerPage) {
    PageRequest pageable = PageRequest.of(
        pageNumber - 1, itemsPerPage, Sort.by(Sort.Direction.DESC, "deletedAt"));
    return noteRepository.findTrashedVisible(currentUserId(), workspaceId, pageable);
  }

  @Override
  public NoteEntity update(Long id, String title, String content, Boolean completed) {
    NoteEntity note = requireEditable(id);
    note.setTitle(title);
    note.setContent(content);
    if (completed != null) {
      note.setCompleted(completed);
    }
    return noteRepository.save(note);
  }

  @Override
  public NoteEntity complete(Long id) {
    NoteEntity note = requireEditable(id);
    note.setCompleted(true);
    return noteRepository.save(note);
  }

  @Override
  public NoteEntity restore(Long id) {
    NoteEntity note = requireEditable(id);
    note.setDeletedAt(null);
    return noteRepository.save(note);
  }

  @Override
  public void delete(Long id) {
    NoteEntity note = requireEditable(id);
    if (note.getDeletedAt() == null) {
      note.setDeletedAt(Instant.now());
      noteRepository.save(note);
    }
  }

  private NoteEntity find(Long id) {
    return noteRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Note " + id + " was not found"));
  }

  /** Loads the note and asserts the caller may edit its workspace. */
  private NoteEntity requireEditable(Long id) {
    NoteEntity note = find(id);
    requireEdit(note.getWorkspaceId(), currentUserId());
    return note;
  }

  private WorkspaceMemberEntity requireMember(Long workspaceId, Long userId) {
    return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
        .orElseThrow(() -> new ForbiddenException("You are not a member of workspace " + workspaceId));
  }

  private void requireEdit(Long workspaceId, Long userId) {
    WorkspaceMemberEntity membership = requireMember(workspaceId, userId);
    if (!WorkspaceRole.CAN_EDIT.contains(membership.getRole())) {
      throw new ForbiddenException("Your role does not allow editing notes in workspace " + workspaceId);
    }
  }

  private Long currentUserId() {
    return userService.currentUser().getId();
  }
}
