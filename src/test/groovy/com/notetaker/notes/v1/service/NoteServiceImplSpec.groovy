package com.notetaker.notes.v1.service

import com.notetaker.notes.v1.exception.NoteAccessDeniedException
import com.notetaker.notes.v1.exception.NoteNotFoundException
import com.notetaker.notes.v1.repository.NoteRepository
import com.notetaker.notes.v1.repository.NoteShareRepository
import com.notetaker.notes.v1.repository.entity.NoteEntity
import com.notetaker.notes.v1.repository.entity.NoteShareEntity
import com.notetaker.notes.v1.repository.entity.NoteShareId
import com.notetaker.security.CurrentUserService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import spock.lang.Specification
import spock.lang.Subject

class NoteServiceImplSpec extends Specification {

  NoteRepository noteRepository = Mock()
  NoteShareRepository noteShareRepository = Mock()
  CurrentUserService currentUserService = Mock()

  @Subject
  NoteServiceImpl service = new NoteServiceImpl(noteRepository, noteShareRepository, currentUserService)

  private static NoteEntity note(String id, String owner) {
    def n = new NoteEntity()
    n.id = id
    n.ownerId = owner
    n.title = 'title'
    n.content = 'content'
    n.completed = false
    return n
  }

  def "create assigns a UUID, current owner, defaults completed=false and persists"() {
    given:
    currentUserService.getCurrentUserId() >> '111'

    when:
    def result = service.create('My title', 'Body')

    then:
    1 * noteRepository.save(_ as NoteEntity) >> { NoteEntity n -> n }
    result.id ==~ /[0-9a-f\-]{36}/
    result.ownerId == '111'
    result.title == 'My title'
    result.content == 'Body'
    !result.completed
  }

  def "create tolerates null content"() {
    given:
    currentUserService.getCurrentUserId() >> '111'

    when:
    def result = service.create('T', null)

    then:
    1 * noteRepository.save(_ as NoteEntity) >> { NoteEntity n -> n }
    result.content == null
  }

  def "get returns the note when the caller is the owner"() {
    given:
    def entity = note('n1', '111')
    noteRepository.findById('n1') >> Optional.of(entity)
    currentUserService.getCurrentUserId() >> '111'

    when:
    def result = service.get('n1')

    then:
    result.is(entity)
    0 * noteShareRepository.existsById(_)
  }

  def "get returns the note when it is shared with the caller"() {
    given:
    def entity = note('n1', '111')
    noteRepository.findById('n1') >> Optional.of(entity)
    currentUserService.getCurrentUserId() >> '222'
    noteShareRepository.existsById(new NoteShareId('n1', '222')) >> true

    when:
    def result = service.get('n1')

    then:
    result.is(entity)
  }

  def "get throws NoteAccessDenied when caller is neither owner nor shared-with"() {
    given:
    def entity = note('n1', '111')
    noteRepository.findById('n1') >> Optional.of(entity)
    currentUserService.getCurrentUserId() >> '999'
    noteShareRepository.existsById(_ as NoteShareId) >> false

    when:
    service.get('n1')

    then:
    thrown(NoteAccessDeniedException)
  }

  def "get throws NoteNotFound when the note does not exist"() {
    given:
    noteRepository.findById('missing') >> Optional.empty()

    when:
    service.get('missing')

    then:
    thrown(NoteNotFoundException)
  }

  def "list without a search term queries the visible-to-user finder, sorted by updatedAt desc"() {
    given:
    currentUserService.getCurrentUserId() >> '111'
    def page = new PageImpl([note('n1', '111')])

    when:
    def result = service.list(1, 20, searchTerm)

    then:
    1 * noteRepository.findVisibleToUser('111', { Pageable p ->
      p.pageNumber == 0 &&
        p.pageSize == 20 &&
        p.sort.getOrderFor('updatedAt').direction == Sort.Direction.DESC
    }) >> page
    0 * noteRepository.findVisibleToUserAndTitle(*_)
    result.content.size() == 1

    where:
    searchTerm << [null, '', '   ']
  }

  def "list with a search term queries the title finder"() {
    given:
    currentUserService.getCurrentUserId() >> '111'
    def page = new PageImpl([note('n1', '111')])

    when:
    def result = service.list(2, 5, 'dock')

    then:
    1 * noteRepository.findVisibleToUserAndTitle('111', 'dock', { Pageable p ->
      p.pageNumber == 1 && p.pageSize == 5
    }) >> page
    0 * noteRepository.findVisibleToUser(*_)
    result.content.size() == 1
  }

  def "update mutates owner-owned note and saves"() {
    given:
    def entity = note('n1', '111')
    noteRepository.findById('n1') >> Optional.of(entity)
    currentUserService.getCurrentUserId() >> '111'

    when:
    def result = service.update('n1', 'new title', 'new body', true)

    then:
    1 * noteRepository.save(entity) >> entity
    result.title == 'new title'
    result.content == 'new body'
    result.completed
  }

  def "update leaves completed unchanged when the flag is null"() {
    given:
    def entity = note('n1', '111')
    entity.completed = true
    noteRepository.findById('n1') >> Optional.of(entity)
    currentUserService.getCurrentUserId() >> '111'

    when:
    def result = service.update('n1', 't', 'c', null)

    then:
    1 * noteRepository.save(entity) >> entity
    result.completed
  }

  def "update by a non-owner is forbidden and never saves"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '222'

    when:
    service.update('n1', 't', 'c', null)

    then:
    thrown(NoteAccessDeniedException)
    0 * noteRepository.save(_)
  }

  def "complete marks the owner-owned note done"() {
    given:
    def entity = note('n1', '111')
    noteRepository.findById('n1') >> Optional.of(entity)
    currentUserService.getCurrentUserId() >> '111'

    when:
    def result = service.complete('n1')

    then:
    1 * noteRepository.save(entity) >> entity
    result.completed
  }

  def "complete by a non-owner is forbidden"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '222'

    when:
    service.complete('n1')

    then:
    thrown(NoteAccessDeniedException)
    0 * noteRepository.save(_)
  }

  def "delete removes shares then the note for the owner"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '111'

    when:
    service.delete('n1')

    then:
    1 * noteShareRepository.deleteByNoteId('n1')
    1 * noteRepository.deleteById('n1')
  }

  def "delete by a non-owner is forbidden and touches nothing"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '222'

    when:
    service.delete('n1')

    then:
    thrown(NoteAccessDeniedException)
    0 * noteShareRepository.deleteByNoteId(_)
    0 * noteRepository.deleteById(_)
  }

  def "delete of a missing note throws NoteNotFound"() {
    given:
    noteRepository.findById('missing') >> Optional.empty()

    when:
    service.delete('missing')

    then:
    thrown(NoteNotFoundException)
  }

  def "share persists a #permission share for the owner"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '111'

    when:
    def result = service.share('n1', '222', permission)

    then:
    1 * noteShareRepository.save({ NoteShareEntity s ->
      s.noteId == 'n1' && s.sharedWithUserId == '222' && s.permission == expected
    }) >> { NoteShareEntity s -> s }
    result.permission == expected

    where:
    permission | expected
    'READ'     | 'READ'
    'WRITE'    | 'WRITE'
    ' WRITE '  | 'WRITE'
  }

  def "share defaults to READ when permission is #permission"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '111'

    when:
    def result = service.share('n1', '222', permission)

    then:
    1 * noteShareRepository.save({ NoteShareEntity s -> s.permission == 'READ' }) >> { NoteShareEntity s -> s }
    result.permission == 'READ'

    where:
    permission << [null, '', '   ']
  }

  def "share rejects an invalid permission with 400-mapped IllegalArgument"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '111'

    when:
    service.share('n1', '222', 'ADMIN')

    then:
    thrown(IllegalArgumentException)
    0 * noteShareRepository.save(_)
  }

  def "share by a non-owner is forbidden"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '222'

    when:
    service.share('n1', '333', 'READ')

    then:
    thrown(NoteAccessDeniedException)
    0 * noteShareRepository.save(_)
  }

  def "listShares returns the note's shares for the owner"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '111'
    def shares = [new NoteShareEntity('n1', '222', 'READ')]

    when:
    def result = service.listShares('n1')

    then:
    1 * noteShareRepository.findByNoteId('n1') >> shares
    result == shares
  }

  def "listShares by a non-owner is forbidden"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '222'

    when:
    service.listShares('n1')

    then:
    thrown(NoteAccessDeniedException)
    0 * noteShareRepository.findByNoteId(_)
  }

  def "unshare deletes an existing share for the owner"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '111'
    noteShareRepository.existsById(new NoteShareId('n1', '222')) >> true

    when:
    service.unshare('n1', '222')

    then:
    1 * noteShareRepository.deleteById(new NoteShareId('n1', '222'))
  }

  def "unshare is a no-op when the share does not exist"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '111'
    noteShareRepository.existsById(_ as NoteShareId) >> false

    when:
    service.unshare('n1', '222')

    then:
    0 * noteShareRepository.deleteById(_)
  }

  def "unshare by a non-owner is forbidden"() {
    given:
    noteRepository.findById('n1') >> Optional.of(note('n1', '111'))
    currentUserService.getCurrentUserId() >> '222'

    when:
    service.unshare('n1', '333')

    then:
    thrown(NoteAccessDeniedException)
    0 * noteShareRepository.deleteById(_)
  }
}
