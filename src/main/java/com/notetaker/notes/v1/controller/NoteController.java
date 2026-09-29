package com.notetaker.notes.v1.controller;

import com.notetaker.api.V1NotesApiDelegate;
import com.notetaker.model.CreateNoteRequest;
import com.notetaker.model.Note;
import com.notetaker.model.NotesResponse;
import com.notetaker.model.PageInfo;
import com.notetaker.model.UpdateNoteRequest;
import com.notetaker.notes.v1.mapper.NoteMapper;
import com.notetaker.notes.v1.repository.entity.NoteEntity;
import com.notetaker.notes.v1.service.NoteService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoteController implements V1NotesApiDelegate {

  private static final int DEFAULT_PAGE_NUMBER = 1;
  private static final int DEFAULT_ITEMS_PER_PAGE = 20;

  private final NoteService noteService;
  private final NoteMapper noteMapper;

  @Override
  public ResponseEntity<Note> createNote(CreateNoteRequest createNoteRequest) {
    NoteEntity note = noteService.create(
        createNoteRequest.getWorkspaceId(),
        createNoteRequest.getTitle(),
        createNoteRequest.getContent());
    return ResponseEntity.status(HttpStatus.CREATED).body(noteMapper.toModel(note));
  }

  @Override
  public ResponseEntity<Note> getNote(Long id) {
    return ResponseEntity.ok(noteMapper.toModel(noteService.get(id)));
  }

  @Override
  public ResponseEntity<NotesResponse> listNotes(
      Long workspaceId, Integer pageNumber, Integer itemsPerPage, String searchTerm) {
    Page<NoteEntity> page = noteService.list(
        workspaceId, resolvePage(pageNumber), resolveSize(itemsPerPage), searchTerm);
    return ResponseEntity.ok(toResponse(page));
  }

  @Override
  public ResponseEntity<NotesResponse> listTrashedNotes(
      Long workspaceId, Integer pageNumber, Integer itemsPerPage) {
    Page<NoteEntity> page = noteService.listTrash(
        workspaceId, resolvePage(pageNumber), resolveSize(itemsPerPage));
    return ResponseEntity.ok(toResponse(page));
  }

  @Override
  public ResponseEntity<Note> updateNote(Long id, UpdateNoteRequest updateNoteRequest) {
    NoteEntity note = noteService.update(
        id,
        updateNoteRequest.getTitle(),
        updateNoteRequest.getContent(),
        updateNoteRequest.getCompleted());
    return ResponseEntity.ok(noteMapper.toModel(note));
  }

  @Override
  public ResponseEntity<Note> completeNote(Long id) {
    return ResponseEntity.ok(noteMapper.toModel(noteService.complete(id)));
  }

  @Override
  public ResponseEntity<Note> restoreNote(Long id) {
    return ResponseEntity.ok(noteMapper.toModel(noteService.restore(id)));
  }

  @Override
  public ResponseEntity<Void> deleteNote(Long id) {
    noteService.delete(id);
    return ResponseEntity.noContent().build();
  }

  private NotesResponse toResponse(Page<NoteEntity> page) {
    List<Note> notes = page.getContent().stream().map(noteMapper::toModel).toList();
    return NotesResponse.builder()
        .notes(notes)
        .pageInfo(PageInfo.builder()
            .pagesCount(page.getTotalPages())
            .totalItems((int) page.getTotalElements())
            .build())
        .build();
  }

  private int resolvePage(Integer pageNumber) {
    return pageNumber == null ? DEFAULT_PAGE_NUMBER : pageNumber;
  }

  private int resolveSize(Integer itemsPerPage) {
    return itemsPerPage == null ? DEFAULT_ITEMS_PER_PAGE : itemsPerPage;
  }
}
