package com.notetaker.notes.v1.controller

import com.notetaker.model.CreateNoteRequest
import com.notetaker.model.ShareNoteRequest
import com.notetaker.model.SharePermission
import com.notetaker.model.UpdateNoteRequest
import com.notetaker.notes.v1.mapper.NoteMapper
import com.notetaker.notes.v1.repository.entity.NoteEntity
import com.notetaker.notes.v1.repository.entity.NoteShareEntity
import com.notetaker.notes.v1.service.NoteService
import com.notetaker.security.CurrentUserService
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject

class NoteControllerSpec extends Specification {

  NoteService noteService = Mock()
  NoteMapper noteMapper = new NoteMapper() {}
  CurrentUserService currentUserService = Mock()

  @Subject
  NoteController controller = new NoteController(noteService, noteMapper, currentUserService)

  private static NoteEntity entity(String id, String owner) {
    def n = new NoteEntity()
    n.id = id
    n.ownerId = owner
    n.title = 'T'
    n.content = 'C'
    return n
  }

  def "createNote returns 201 with shared=false"() {
    given:
    noteService.create('T', 'C') >> entity('n1', '111')

    when:
    def response = controller.createNote(new CreateNoteRequest(title: 'T', content: 'C'))

    then:
    response.statusCode == HttpStatus.CREATED
    response.body.id == 'n1'
    !response.body.shared
  }

  def "getNote flags shared=true when the caller is not the owner"() {
    given:
    noteService.get('n1') >> entity('n1', '111')
    currentUserService.getCurrentUserId() >> '222'

    when:
    def response = controller.getNote('n1')

    then:
    response.statusCode == HttpStatus.OK
    response.body.shared
  }

  def "getNote flags shared=false when the caller is the owner"() {
    given:
    noteService.get('n1') >> entity('n1', '111')
    currentUserService.getCurrentUserId() >> '111'

    expect:
    !controller.getNote('n1').body.shared
  }

  def "listNotes defaults paging, builds page info, and flags shared per note"() {
    given:
    currentUserService.getCurrentUserId() >> '111'
    def owned = entity('n1', '111')
    def sharedWithMe = entity('n2', '999')
    noteService.list(1, 20, null) >> new PageImpl([owned, sharedWithMe], PageRequest.of(0, 20), 2)

    when:
    def response = controller.listNotes(null, null, null)

    then:
    response.body.notes.size() == 2
    !response.body.notes[0].shared
    response.body.notes[1].shared
    response.body.pageInfo.totalItems == 2
    response.body.pageInfo.pagesCount == 1
  }

  def "listNotes passes explicit paging and search term through"() {
    given:
    currentUserService.getCurrentUserId() >> '111'
    noteService.list(3, 5, 'dock') >> new PageImpl([], PageRequest.of(2, 5), 0)

    when:
    def response = controller.listNotes(3, 5, 'dock')

    then:
    response.body.notes.isEmpty()
    1 * noteService.list(3, 5, 'dock') >> new PageImpl([], PageRequest.of(2, 5), 0)
  }

  def "updateNote delegates all fields and returns 200"() {
    given:
    def request = new UpdateNoteRequest(title: 'T2', content: 'C2', completed: true)
    noteService.update('n1', 'T2', 'C2', true) >> entity('n1', '111')

    when:
    def response = controller.updateNote('n1', request)

    then:
    response.statusCode == HttpStatus.OK
    response.body.id == 'n1'
  }

  def "completeNote returns 200"() {
    given:
    noteService.complete('n1') >> entity('n1', '111')

    expect:
    controller.completeNote('n1').statusCode == HttpStatus.OK
  }

  def "deleteNote returns 204"() {
    when:
    def response = controller.deleteNote('n1')

    then:
    1 * noteService.delete('n1')
    response.statusCode == HttpStatus.NO_CONTENT
  }

  def "shareNote maps the enum permission to its value and returns 201"() {
    given:
    def request = new ShareNoteRequest(sharedWithUserId: '222', permission: SharePermission.WRITE)

    when:
    def response = controller.shareNote('n1', request)

    then:
    1 * noteService.share('n1', '222', 'WRITE') >> new NoteShareEntity('n1', '222', 'WRITE')
    response.statusCode == HttpStatus.CREATED
    response.body.permission == SharePermission.WRITE
  }

  def "shareNote passes null permission through when unspecified"() {
    given:
    def request = new ShareNoteRequest(sharedWithUserId: '222', permission: null)

    when:
    controller.shareNote('n1', request)

    then:
    1 * noteService.share('n1', '222', null) >> new NoteShareEntity('n1', '222', 'READ')
  }

  def "listNoteShares wraps the service result"() {
    given:
    noteService.listShares('n1') >> [new NoteShareEntity('n1', '222', 'READ')]

    when:
    def response = controller.listNoteShares('n1')

    then:
    response.body.shares.size() == 1
    response.body.shares[0].sharedWithUserId == '222'
  }

  def "unshareNote returns 204"() {
    when:
    def response = controller.unshareNote('n1', '222')

    then:
    1 * noteService.unshare('n1', '222')
    response.statusCode == HttpStatus.NO_CONTENT
  }
}
