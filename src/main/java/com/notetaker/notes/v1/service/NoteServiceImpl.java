package com.notetaker.notes.v1.service;

import com.notetaker.notes.v1.exception.NoteAccessDeniedException;
import com.notetaker.notes.v1.exception.NoteNotFoundException;
import com.notetaker.notes.v1.repository.NoteRepository;
import com.notetaker.notes.v1.repository.NoteShareRepository;
import com.notetaker.notes.v1.repository.entity.NoteEntity;
import com.notetaker.notes.v1.repository.entity.NoteShareEntity;
import com.notetaker.notes.v1.repository.entity.NoteShareId;
import com.notetaker.security.CurrentUserService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

  private static final String READ_PERMISSION = "READ";
  private static final Set<String> VALID_PERMISSIONS = Set.of(READ_PERMISSION, "WRITE");

  private final NoteRepository noteRepository;
  private final NoteShareRepository noteShareRepository;
  private final CurrentUserService currentUserService;

  @Override
  public NoteEntity create(String title, String content) {
    NoteEntity note = new NoteEntity();
    note.setId(UUID.randomUUID().toString());
    note.setOwnerId(currentUserService.getCurrentUserId());
    note.setTitle(title);
    note.setContent(content);
    note.setCompleted(false);
    return noteRepository.save(note);
  }

  @Override
  @Transactional(readOnly = true)
  public NoteEntity get(String id) {
    NoteEntity note = noteRepository.findById(id)
        .orElseThrow(() -> new NoteNotFoundException(id));
    String currentUserId = currentUserService.getCurrentUserId();

    if (note.getOwnerId().equals(currentUserId)
        || noteShareRepository.existsById(new NoteShareId(id, currentUserId))) {
      return note;
    }

    throw new NoteAccessDeniedException("User may not access note " + id);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<NoteEntity> list(int pageNumber, int itemsPerPage, String searchTerm) {
    String currentUserId = currentUserService.getCurrentUserId();
    PageRequest pageable = PageRequest.of(pageNumber - 1, itemsPerPage, Sort.by(Sort.Direction.DESC, "updatedAt"));

    if (!StringUtils.hasText(searchTerm)) {
      return noteRepository.findVisibleToUser(currentUserId, pageable);
    }

    return noteRepository.findVisibleToUserAndTitle(currentUserId, searchTerm, pageable);
  }

  @Override
  public NoteEntity update(String id, String title, String content, Boolean completed) {
    NoteEntity note = requireOwner(id);
    note.setTitle(title);
    note.setContent(content);
    if (completed != null) {
      note.setCompleted(completed);
    }
    return noteRepository.save(note);
  }

  @Override
  public NoteEntity complete(String id) {
    NoteEntity note = requireOwner(id);
    note.setCompleted(true);
    return noteRepository.save(note);
  }

  @Override
  public void delete(String id) {
    requireOwner(id);
    noteShareRepository.deleteByNoteId(id);
    noteRepository.deleteById(id);
  }

  @Override
  public NoteShareEntity share(String id, String sharedWithUserId, String permission) {
    requireOwner(id);
    String resolvedPermission = StringUtils.hasText(permission) ? permission.trim() : READ_PERMISSION;
    if (!VALID_PERMISSIONS.contains(resolvedPermission)) {
      throw new IllegalArgumentException("Permission must be READ or WRITE");
    }

    return noteShareRepository.save(new NoteShareEntity(id, sharedWithUserId, resolvedPermission));
  }

  @Override
  @Transactional(readOnly = true)
  public List<NoteShareEntity> listShares(String id) {
    requireOwner(id);
    return noteShareRepository.findByNoteId(id);
  }

  @Override
  public void unshare(String id, String userId) {
    requireOwner(id);
    NoteShareId key = new NoteShareId(id, userId);
    if (noteShareRepository.existsById(key)) {
      noteShareRepository.deleteById(key);
    }
  }

  private NoteEntity requireOwner(String id) {
    NoteEntity note = noteRepository.findById(id)
        .orElseThrow(() -> new NoteNotFoundException(id));
    String currentUserId = currentUserService.getCurrentUserId();
    if (!note.getOwnerId().equals(currentUserId)) {
      throw new NoteAccessDeniedException("Only the owner may modify note " + id);
    }
    return note;
  }
}
