package com.notetaker.notes.v1.service

import com.notetaker.exception.ForbiddenException
import com.notetaker.exception.NotFoundException
import com.notetaker.notes.v1.repository.WorkspaceMemberRepository
import com.notetaker.notes.v1.repository.WorkspaceRepository
import com.notetaker.notes.v1.repository.entity.AppUserEntity
import com.notetaker.notes.v1.repository.entity.WorkspaceEntity
import com.notetaker.notes.v1.repository.entity.WorkspaceMemberEntity
import spock.lang.Specification
import spock.lang.Subject

class WorkspaceServiceSpec extends Specification {

  WorkspaceRepository workspaceRepository = Mock()
  WorkspaceMemberRepository memberRepository = Mock()
  UserService userService = Mock()

  @Subject
  WorkspaceService service = new WorkspaceService(workspaceRepository, memberRepository, userService)

  private static AppUserEntity user(long id, String emp = "emp-${id}") {
    def u = new AppUserEntity()
    u.id = id
    u.employeeNumber = emp
    u.displayName = emp
    return u
  }

  private static WorkspaceEntity workspace(long id, String name = 'ws') {
    def w = new WorkspaceEntity()
    w.id = id
    w.name = name
    return w
  }

  private static WorkspaceMemberEntity member(long workspaceId, long userId, String role) {
    new WorkspaceMemberEntity(workspaceId, userId, role)
  }

  def "create saves the workspace and makes the caller its owner"() {
    given:
    userService.currentUser() >> user(1)

    when:
    def result = service.create('Dock 12 crew')

    then:
    1 * workspaceRepository.save(_ as WorkspaceEntity) >> { WorkspaceEntity w -> w.id = 100; w }
    1 * memberRepository.save({ WorkspaceMemberEntity m ->
      m.workspaceId == 100L && m.userId == 1L && m.role == 'OWNER'
    }) >> { WorkspaceMemberEntity m -> m }
    result.workspace().id == 100L
    result.role() == 'OWNER'
  }

  def "listMine returns each workspace paired with the caller's role, ordered by id"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByUserId(1L) >> [member(20L, 1L, 'VIEWER'), member(10L, 1L, 'OWNER')]
    workspaceRepository.findAllById(_) >> [workspace(10L, 'a'), workspace(20L, 'b')]

    when:
    def result = service.listMine()

    then:
    result*.workspace()*.id == [10L, 20L]
    result*.role() == ['OWNER', 'VIEWER']
  }

  def "listMembers resolves each member's user for a member caller"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'VIEWER'))
    memberRepository.findByWorkspaceId(10L) >> [member(10L, 1L, 'OWNER'), member(10L, 2L, 'EDITOR')]
    userService.findAll(_) >> [user(1), user(2)]

    when:
    def result = service.listMembers(10L)

    then:
    result*.user()*.id == [1L, 2L]
    result*.role() == ['OWNER', 'EDITOR']
  }

  def "listMembers is forbidden for a non-member"() {
    given:
    userService.currentUser() >> user(9)
    memberRepository.findByWorkspaceIdAndUserId(10L, 9L) >> Optional.empty()

    when:
    service.listMembers(10L)

    then:
    thrown(ForbiddenException)
  }

  def "addMember provisions the target and stores the requested role for an owner"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'OWNER'))
    def target = user(2, '987654')
    userService.getOrCreate('987654') >> target
    memberRepository.findByWorkspaceIdAndUserId(10L, 2L) >> Optional.empty()

    when:
    def result = service.addMember(10L, '987654', 'EDITOR')

    then:
    1 * memberRepository.save({ WorkspaceMemberEntity m ->
      m.workspaceId == 10L && m.userId == 2L && m.role == 'EDITOR'
    }) >> { WorkspaceMemberEntity m -> m }
    result.user().id == 2L
    result.role() == 'EDITOR'
    result.created()
  }

  def "addMember defaults to VIEWER when no role is supplied"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'OWNER'))
    userService.getOrCreate('987654') >> user(2, '987654')
    memberRepository.findByWorkspaceIdAndUserId(10L, 2L) >> Optional.empty()

    when:
    def result = service.addMember(10L, '987654', role)

    then:
    1 * memberRepository.save({ WorkspaceMemberEntity m -> m.role == 'VIEWER' }) >> { WorkspaceMemberEntity m -> m }
    result.role() == 'VIEWER'

    where:
    role << [null, '', '  ']
  }

  def "addMember rejects an invalid role"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'OWNER'))

    when:
    service.addMember(10L, '987654', 'ADMIN')

    then:
    thrown(IllegalArgumentException)
    0 * memberRepository.save(_)
  }

  def "addMember updates the role of an existing member"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'OWNER'))
    userService.getOrCreate('987654') >> user(2, '987654')
    memberRepository.findByWorkspaceIdAndUserId(10L, 2L) >> Optional.of(member(10L, 2L, 'VIEWER'))

    when:
    def result = service.addMember(10L, '987654', 'EDITOR')

    then:
    1 * memberRepository.save({ WorkspaceMemberEntity m -> m.role == 'EDITOR' }) >> { WorkspaceMemberEntity m -> m }
    result.role() == 'EDITOR'
    !result.created()
  }

  def "addMember is forbidden for a non-owner"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'EDITOR'))

    when:
    service.addMember(10L, '987654', 'EDITOR')

    then:
    thrown(ForbiddenException)
    0 * memberRepository.save(_)
  }

  def "removeMember deletes a non-owner member for an owner caller"() {
    given:
    userService.currentUser() >> user(1)
    def owner = member(10L, 1L, 'OWNER')
    def editor = member(10L, 2L, 'EDITOR')
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(owner)
    memberRepository.findByWorkspaceId(10L) >> [owner, editor]

    when:
    service.removeMember(10L, 2L)

    then:
    1 * memberRepository.delete(editor)
  }

  def "removeMember refuses to remove the last owner"() {
    given:
    userService.currentUser() >> user(1)
    def owner = member(10L, 1L, 'OWNER')
    def viewer = member(10L, 2L, 'VIEWER')
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(owner)
    memberRepository.findByWorkspaceId(10L) >> [owner, viewer]

    when:
    service.removeMember(10L, 1L)

    then:
    thrown(ForbiddenException)
    0 * memberRepository.delete(_)
  }

  def "removeMember throws NotFound when the target is not a member"() {
    given:
    userService.currentUser() >> user(1)
    def owner = member(10L, 1L, 'OWNER')
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(owner)
    memberRepository.findByWorkspaceId(10L) >> [owner]

    when:
    service.removeMember(10L, 999L)

    then:
    thrown(NotFoundException)
    0 * memberRepository.delete(_)
  }

  def "removeMember is forbidden for a non-owner"() {
    given:
    userService.currentUser() >> user(1)
    memberRepository.findByWorkspaceIdAndUserId(10L, 1L) >> Optional.of(member(10L, 1L, 'VIEWER'))

    when:
    service.removeMember(10L, 2L)

    then:
    thrown(ForbiddenException)
    0 * memberRepository.delete(_)
  }
}
