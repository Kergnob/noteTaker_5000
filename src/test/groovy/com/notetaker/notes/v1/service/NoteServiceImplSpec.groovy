package com.notetaker.notes.v1.service

import com.notetaker.exception.ForbiddenException
import com.notetaker.exception.NotFoundException
import com.notetaker.notes.v1.repository.NoteRepository
import com.notetaker.notes.v1.repository.WorkspaceMemberRepository
import com.notetaker.notes.v1.repository.entity.AppUserEntity
import com.notetaker.notes.v1.repository.entity.NoteEntity
import com.notetaker.notes.v1.repository.entity.WorkspaceMemberEntity
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class NoteServiceImplSpec extends Specification {

  NoteRepository noteRepository = Mock()
  WorkspaceMemberRepository memberRepository = Mock()
  UserService userService = Mock()

  @Subject
  NoteServiceImpl service = new NoteServiceImpl(noteRepository, memberRepository, userService)

  private static AppUserEntity user(long id) {
    def u = new AppUserEntity()
    u.id = id
    return u
  }

  private static NoteEntity note(long id, long workspaceId, long creator) {
    def n = new NoteEntity()
    n.id = id
    n.workspaceId = workspaceId
    n.createdByUserId = creator
    n.title = 'title'
    n.content = 'content'
    n.completed = false
    return n
  }

  private static WorkspaceMemberEntity member(long workspaceId, long userId, String role) {
    new WorkspaceMemberEntity(workspaceId, userId, role)
  }

  def "create persists a note for an editor with defaults"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, role))

    when:
    def result = service.create(10L, 'My title', 'Body')

    then:
    1 * noteRepository.save(_ as NoteEntity) >> { NoteEntity n -> n }
    result.workspaceId == 10L
    result.createdByUserId == 1L
    result.title == 'My title'
    result.content == 'Body'
    !result.completed

    where:
    role << ['OWNER', 'EDITOR']
  }

  def "create is forbidden for a viewer and never saves"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'VIEWER'))

    when:
    service.create(10L, 'T', 'B')

    then:
    thrown(ForbiddenException)
    0 * noteRepository.save(_)
  }

  def "create is forbidden for a non-member"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.empty()

    when:
    service.create(10L, 'T', 'B')

    then:
    thrown(ForbiddenException)
    0 * noteRepository.save(_)
  }

  def "get returns the note for any member including a viewer"() {
    given:
    def entity = note(5L, 10L, 2L)
    noteRepository.findById(5L) >> Optional.of(entity)
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'VIEWER'))

    when:
    def result = service.get(5L)

    then:
    result.is(entity)
  }

  def "get is forbidden when the caller is not a member of the note's workspace"() {
    given:
    noteRepository.findById(5L) >> Optional.of(note(5L, 10L, 2L))
    userService.currentUser() >> user(9)
    memberRepository.findByWorkspaceIdAndUserId(10L, 9L) >> Optional.empty()

    when:
    service.get(5L)

    then:
    thrown(ForbiddenException)
  }

  def "get throws NotFound when the note does not exist"() {
    given:
    noteRepository.findById(404L) >> Optional.empty()

    when:
    service.get(404L)

    then:
    thrown(NotFoundException)
  }

  def "list without a search term queries active notes sorted by updatedAt desc"() {
    given:
    userService.currentUser() >> user(1)
    def page = new PageImpl([note(1L, 10L, 1L)])

    when:
    def result = service.list(10L, 1, 20, searchTerm)

    then:
    1 * noteRepository.findActiveVisible(1L, 10L, null, { Pageable p ->
      p.pageNumber == 0 &&
        p.pageSize == 20 &&
        p.sort.getOrderFor('updatedAt').direction == Sort.Direction.DESC
    }) >> page
    result.content.size() == 1

    where:
    searchTerm << [null, '', '   ']
  }

  def "list with a search term passes the term through"() {
    given:
    userService.currentUser() >> user(1)
    def page = new PageImpl([note(1L, 10L, 1L)])

    when:
    def result = service.list(null, 2, 5, 'dock')

    then:
    1 * noteRepository.findActiveVisible(1L, null, 'dock', { Pageable p ->
      p.pageNumber == 1 && p.pageSize == 5
    }) >> page
    result.content.size() == 1
  }

  def "listTrash queries trashed notes sorted by deletedAt desc"() {
    given:
    userService.currentUser() >> user(1)
    def page = new PageImpl([note(1L, 10L, 1L)])

    when:
    def result = service.listTrash(10L, 1, 20)

    then:
    1 * noteRepository.findTrashedVisible(1L, 10L, { Pageable p ->
      p.sort.getOrderFor('deletedAt').direction == Sort.Direction.DESC
    }) >> page
    result.content.size() == 1
  }

  def "update mutates an editable note and saves"() {
    given:
    def entity = note(5L, 10L, 2L)
    noteRepository.findById(5L) >> Optional.of(entity)
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'EDITOR'))

    when:
    def result = service.update(5L, 'new title', 'new body', true)

    then:
    1 * noteRepository.save(entity) >> entity
    result.title == 'new title'
    result.content == 'new body'
    result.completed
  }

  def "update leaves completed unchanged when the flag is null"() {
    given:
    def entity = note(5L, 10L, 2L)
    entity.completed = true
    noteRepository.findById(5L) >> Optional.of(entity)
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'OWNER'))

    when:
    def result = service.update(5L, 't', 'c', null)

    then:
    1 * noteRepository.save(entity) >> entity
    result.completed
  }

  def "update by a viewer is forbidden and never saves"() {
    given:
    noteRepository.findById(5L) >> Optional.of(note(5L, 10L, 2L))
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'VIEWER'))

    when:
    service.update(5L, 't', 'c', null)

    then:
    thrown(ForbiddenException)
    0 * noteRepository.save(_)
  }

  def "complete marks an editable note done"() {
    given:
    def entity = note(5L, 10L, 2L)
    noteRepository.findById(5L) >> Optional.of(entity)
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'EDITOR'))

    when:
    def result = service.complete(5L)

    then:
    1 * noteRepository.save(entity) >> entity
    result.completed
  }

  def "restore clears the deletedAt timestamp"() {
    given:
    def entity = note(5L, 10L, 2L)
    entity.deletedAt = Instant.now()
    noteRepository.findById(5L) >> Optional.of(entity)
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'EDITOR'))

    when:
    def result = service.restore(5L)

    then:
    1 * noteRepository.save(entity) >> entity
    result.deletedAt == null
  }

  def "delete soft-deletes an active note by stamping deletedAt"() {
    given:
    def entity = note(5L, 10L, 2L)
    noteRepository.findById(5L) >> Optional.of(entity)
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'OWNER'))

    when:
    service.delete(5L)

    then:
    1 * noteRepository.save({ NoteEntity n -> n.deletedAt != null }) >> { NoteEntity n -> n }
  }

  def "delete is idempotent for an already-trashed note"() {
    given:
    def entity = note(5L, 10L, 2L)
    entity.deletedAt = Instant.now()
    noteRepository.findById(5L) >> Optional.of(entity)
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'OWNER'))

    when:
    service.delete(5L)

    then:
    0 * noteRepository.save(_)
  }

  def "delete by a viewer is forbidden and touches nothing"() {
    given:
    noteRepository.findById(5L) >> Optional.of(note(5L, 10L, 2L))
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'VIEWER'))

    when:
    service.delete(5L)

    then:
    thrown(ForbiddenException)
    0 * noteRepository.save(_)
  }

  def "delete of a missing note throws NotFound"() {
    given:
    noteRepository.findById(404L) >> Optional.empty()

    when:
    service.delete(404L)

    then:
    thrown(NotFoundException)
  }
}
