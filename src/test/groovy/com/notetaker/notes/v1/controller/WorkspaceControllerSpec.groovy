package com.notetaker.notes.v1.controller

import com.notetaker.model.AddMemberRequest
import com.notetaker.model.CreateWorkspaceRequest
import com.notetaker.model.WorkspaceRole
import com.notetaker.notes.v1.mapper.WorkspaceMapper
import com.notetaker.notes.v1.repository.entity.AppUserEntity
import com.notetaker.notes.v1.repository.entity.WorkspaceEntity
import com.notetaker.notes.v1.service.WorkspaceService
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class WorkspaceControllerSpec extends Specification {

  WorkspaceService workspaceService = Mock()
  WorkspaceMapper workspaceMapper = new WorkspaceMapper() {}

  @Subject
  WorkspaceController controller = new WorkspaceController(workspaceService, workspaceMapper)

  private static WorkspaceEntity workspace(long id, String name = 'ws') {
    def w = new WorkspaceEntity()
    w.id = id
    w.name = name
    return w
  }

  private static AppUserEntity user(long id, String emp) {
    def u = new AppUserEntity()
    u.id = id
    u.employeeNumber = emp
    u.displayName = emp
    return u
  }

  def "createWorkspace returns 201 with the caller as owner"() {
    given:
    workspaceService.create('Dock 12 crew') >> new WorkspaceService.MembershipView(workspace(100L, 'Dock 12 crew'), 'OWNER')

    when:
    def response = controller.createWorkspace(new CreateWorkspaceRequest(name: 'Dock 12 crew'))

    then:
    response.statusCode == HttpStatus.CREATED
    response.body.id == 100L
    response.body.role == WorkspaceRole.OWNER
  }

  def "listWorkspaces maps each membership view"() {
    given:
    workspaceService.listMine() >> [
      new WorkspaceService.MembershipView(workspace(10L, 'a'), 'OWNER'),
      new WorkspaceService.MembershipView(workspace(20L, 'b'), 'VIEWER')
    ]

    when:
    def response = controller.listWorkspaces()

    then:
    response.body.workspaces*.id == [10L, 20L]
    response.body.workspaces*.role == [WorkspaceRole.OWNER, WorkspaceRole.VIEWER]
  }

  def "listWorkspaceMembers maps each member view"() {
    given:
    workspaceService.listMembers(10L) >> [
      new WorkspaceService.MemberView(user(1L, '111'), 'OWNER', Instant.now(), false),
      new WorkspaceService.MemberView(user(2L, '222'), 'EDITOR', Instant.now(), false)
    ]

    when:
    def response = controller.listWorkspaceMembers(10L)

    then:
    response.body.members*.userId == [1L, 2L]
    response.body.members*.employeeNumber == ['111', '222']
  }

  def "addWorkspaceMember maps the enum role to its value and returns 201 on create"() {
    when:
    def response = controller.addWorkspaceMember(10L, new AddMemberRequest(employeeNumber: '987654', role: WorkspaceRole.EDITOR))

    then:
    1 * workspaceService.addMember(10L, '987654', 'EDITOR') >> new WorkspaceService.MemberView(user(2L, '987654'), 'EDITOR', Instant.now(), true)
    response.statusCode == HttpStatus.CREATED
    response.body.role == WorkspaceRole.EDITOR
  }

  def "addWorkspaceMember returns 200 when an existing member's role is updated"() {
    when:
    def response = controller.addWorkspaceMember(10L, new AddMemberRequest(employeeNumber: '987654', role: WorkspaceRole.EDITOR))

    then:
    1 * workspaceService.addMember(10L, '987654', 'EDITOR') >> new WorkspaceService.MemberView(user(2L, '987654'), 'EDITOR', Instant.now(), false)
    response.statusCode == HttpStatus.OK
    response.body.role == WorkspaceRole.EDITOR
  }

  def "addWorkspaceMember passes a null role through when unspecified"() {
    when:
    controller.addWorkspaceMember(10L, new AddMemberRequest(employeeNumber: '987654', role: null))

    then:
    1 * workspaceService.addMember(10L, '987654', null) >> new WorkspaceService.MemberView(user(2L, '987654'), 'VIEWER', Instant.now(), true)
  }

  def "removeWorkspaceMember returns 204"() {
    when:
    def response = controller.removeWorkspaceMember(10L, 2L)

    then:
    1 * workspaceService.removeMember(10L, 2L)
    response.statusCode == HttpStatus.NO_CONTENT
  }
}
