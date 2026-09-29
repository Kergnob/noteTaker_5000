package com.notetaker.notes.v1.controller;

import com.notetaker.api.V1NotesApiDelegate;
import com.notetaker.model.CreateNoteRequest;
import com.notetaker.model.Note;
import com.notetaker.model.NoteShare;
import com.notetaker.model.NoteSharesResponse;
import com.notetaker.model.NotesResponse;
import com.notetaker.model.PageInfo;
import com.notetaker.model.ShareNoteRequest;
import com.notetaker.model.UpdateNoteRequest;
import com.notetaker.notes.v1.mapper.NoteMapper;
import com.notetaker.notes.v1.repository.entity.NoteEntity;
import com.notetaker.notes.v1.repository.entity.NoteShareEntity;
import com.notetaker.notes.v1.service.NoteService;
import com.notetaker.security.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoteController implements V1NotesApiDelegate {

  private final NoteService noteService;
  private final NoteMapper noteMapper;
  private final CurrentUserService currentUserService;

  @Override
  public ResponseEntity<Note> createNote(CreateNoteRequest createNoteRequest) {
    Note note = noteMapper.toModel(noteService.create(createNoteRequest.getTitle(), createNoteRequest.getContent()));
    note.setShared(false);
    return ResponseEntity.status(HttpStatus.CREATED).body(note);
  }

  @Override
  public ResponseEntity<Note> getNote(String id) {
    NoteEntity entity = noteService.get(id);
    Note note = noteMapper.toModel(entity);
    note.setShared(!entity.getOwnerId().equals(currentUserService.getCurrentUserId()));
    return ResponseEntity.ok(note);
  }

  @Override
  public ResponseEntity<NotesResponse> listNotes(Integer pageNumber, Integer itemsPerPage, String searchTerm) {
    int resolvedPageNumber = pageNumber == null ? 1 : pageNumber;
    int resolvedItemsPerPage = itemsPerPage == null ? 20 : itemsPerPage;
    String currentUserId = currentUserService.getCurrentUserId();
    Page<NoteEntity> page = noteService.list(resolvedPageNumber, resolvedItemsPerPage, searchTerm);

    List<Note> notes = page.getContent().stream()
        .map(entity -> {
          Note note = noteMapper.toModel(entity);
          note.setShared(!entity.getOwnerId().equals(currentUserId));
          return note;
        })
        .toList();

    NotesResponse response = NotesResponse.builder()
        .notes(notes)
        .pageInfo(PageInfo.builder()
            .pagesCount(page.getTotalPages())
            .totalItems((int) page.getTotalElements())
            .build())
        .build();

    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<Note> updateNote(String id, UpdateNoteRequest updateNoteRequest) {
    Note note = noteMapper.toModel(noteService.update(
        id,
        updateNoteRequest.getTitle(),
        updateNoteRequest.getContent(),
        updateNoteRequest.getCompleted()));
    note.setShared(false);
    return ResponseEntity.ok(note);
  }

  @Override
  public ResponseEntity<Note> completeNote(String id) {
    Note note = noteMapper.toModel(noteService.complete(id));
    note.setShared(false);
    return ResponseEntity.ok(note);
  }

  @Override
  public ResponseEntity<Void> deleteNote(String id) {
    noteService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<NoteShare> shareNote(String id, ShareNoteRequest shareNoteRequest) {
    String permission = shareNoteRequest.getPermission() == null ? null : shareNoteRequest.getPermission().getValue();
    NoteShareEntity share = noteService.share(id, shareNoteRequest.getSharedWithUserId(), permission);
    return ResponseEntity.status(HttpStatus.CREATED).body(noteMapper.toModel(share));
  }

  @Override
  public ResponseEntity<NoteSharesResponse> listNoteShares(String id) {
    NoteSharesResponse response = NoteSharesResponse.builder()
        .shares(noteService.listShares(id).stream().map(noteMapper::toModel).toList())
        .build();
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<Void> unshareNote(String id, String userId) {
    noteService.unshare(id, userId);
    return ResponseEntity.noContent().build();
  }
}
