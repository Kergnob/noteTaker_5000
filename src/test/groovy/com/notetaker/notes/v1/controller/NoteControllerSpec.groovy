package com.notetaker.notes.v1.controller

import com.notetaker.model.CreateNoteRequest
import com.notetaker.model.UpdateNoteRequest
import com.notetaker.notes.v1.mapper.NoteMapper
import com.notetaker.notes.v1.repository.entity.NoteEntity
import com.notetaker.notes.v1.service.NoteService
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject

class NoteControllerSpec extends Specification {

  NoteService noteService = Mock()
  NoteMapper noteMapper = new NoteMapper() {}

  @Subject
  NoteController controller = new NoteController(noteService, noteMapper)

  private static NoteEntity entity(long id, long workspaceId = 12L) {
    def n = new NoteEntity()
    n.id = id
    n.workspaceId = workspaceId
    n.createdByUserId = 55L
    n.title = 'T'
    n.content = 'C'
    return n
  }

  def "createNote delegates to the service and returns 201"() {
    given:
    noteService.create(12L, 'T', 'C') >> entity(1L)

    when:
    def response = controller.createNote(new CreateNoteRequest(workspaceId: 12L, title: 'T', content: 'C'))

    then:
    response.statusCode == HttpStatus.CREATED
    response.body.id == 1L
    response.body.workspaceId == 12L
  }

  def "getNote returns the mapped note"() {
    given:
    noteService.get(1L) >> entity(1L)

    when:
    def response = controller.getNote(1L)

    then:
    response.statusCode == HttpStatus.OK
    response.body.id == 1L
  }

  def "listNotes defaults paging and builds page info"() {
    given:
    noteService.list(null, 1, 20, null) >> new PageImpl([entity(1L), entity(2L)], PageRequest.of(0, 20), 2)

    when:
    def response = controller.listNotes(null, null, null, null)

    then:
    response.body.notes.size() == 2
    response.body.pageInfo.totalItems == 2
    response.body.pageInfo.pagesCount == 1
  }

  def "listNotes passes explicit workspace, paging and search term through"() {
    when:
    def response = controller.listNotes(12L, 3, 5, 'dock')

    then:
    1 * noteService.list(12L, 3, 5, 'dock') >> new PageImpl([], PageRequest.of(2, 5), 0)
    response.body.notes.isEmpty()
  }

  def "listTrashedNotes delegates to the trash listing"() {
    when:
    def response = controller.listTrashedNotes(12L, 1, 20)

    then:
    1 * noteService.listTrash(12L, 1, 20) >> new PageImpl([entity(9L)], PageRequest.of(0, 20), 1)
    response.body.notes[0].id == 9L
  }

  def "updateNote delegates all fields and returns 200"() {
    given:
    noteService.update(1L, 'T2', 'C2', true) >> entity(1L)

    when:
    def response = controller.updateNote(1L, new UpdateNoteRequest(title: 'T2', content: 'C2', completed: true))

    then:
    response.statusCode == HttpStatus.OK
    response.body.id == 1L
  }

  def "completeNote returns 200"() {
    given:
    noteService.complete(1L) >> entity(1L)

    expect:
    controller.completeNote(1L).statusCode == HttpStatus.OK
  }

  def "restoreNote returns 200"() {
    given:
    noteService.restore(1L) >> entity(1L)

    expect:
    controller.restoreNote(1L).statusCode == HttpStatus.OK
  }

  def "deleteNote returns 204"() {
    when:
    def response = controller.deleteNote(1L)

    then:
    1 * noteService.delete(1L)
    response.statusCode == HttpStatus.NO_CONTENT
  }
}
