package com.notetaker.component

import com.notetaker.model.AddMemberRequest
import com.notetaker.model.CreateNoteRequest
import com.notetaker.model.CreateWorkspaceRequest
import com.notetaker.model.Note
import com.notetaker.model.NotesResponse
import com.notetaker.model.UpdateNoteRequest
import com.notetaker.model.Workspace
import com.notetaker.model.WorkspaceMember
import com.notetaker.model.WorkspaceMembersResponse
import com.notetaker.model.WorkspaceRole
import com.notetaker.model.WorkspacesResponse
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.Instant

class NoteApiComponentSpec extends NoteComponentBaseSpec {
  static final String USER_A = 'A-1001'
  static final String USER_B = 'B-2002'
  static final String USER_C = 'C-3003'

  // ---- workspace lifecycle --------------------------------------------------------------------

  def "creating a workspace makes the caller its owner and lists it"() {
    when: "A creates a workspace"
    def created = rest.exchange(workspacesUrl(), HttpMethod.POST,
      asUser(USER_A, new CreateWorkspaceRequest(name: 'Dock 12 crew')), Workspace)

    then:
    created.statusCode == HttpStatus.CREATED
    created.body.id != null
    created.body.role == WorkspaceRole.OWNER

    and: "an APP_USER row was provisioned for A and A owns the workspace"
    def userA = appUserRepository.findByEmployeeNumber(USER_A).orElse(null)
    userA != null
    memberRepository.findByWorkspaceIdAndUserId(created.body.id, userA.id).get().role == 'OWNER'

    when: "A lists their workspaces"
    def list = rest.exchange(workspacesUrl(), HttpMethod.GET, asUser(USER_A), WorkspacesResponse)

    then:
    list.body.workspaces*.id == [created.body.id]
  }

  def "an owner can add a member who then gains access, and can list members"() {
    given: "A owns a workspace"
    def (workspaceId, ownerId) = seedMembership(USER_A, 'OWNER')

    when: "A adds B as an editor by employee number"
    def added = rest.exchange("${workspacesUrl()}/${workspaceId}/members", HttpMethod.POST,
      asUser(USER_A, new AddMemberRequest(employeeNumber: USER_B, role: WorkspaceRole.EDITOR)), WorkspaceMember)

    then:
    added.statusCode == HttpStatus.CREATED
    added.body.employeeNumber == USER_B
    added.body.role == WorkspaceRole.EDITOR

    and: "B was provisioned and is now a member"
    def userB = appUserRepository.findByEmployeeNumber(USER_B).orElse(null)
    userB != null
    memberRepository.findByWorkspaceIdAndUserId(workspaceId, userB.id).isPresent()

    when: "any member lists the workspace members"
    def members = rest.exchange("${workspacesUrl()}/${workspaceId}/members", HttpMethod.GET,
      asUser(USER_B), WorkspaceMembersResponse)

    then:
    members.body.members*.employeeNumber as Set == [USER_A, USER_B] as Set
  }

  def "re-adding an existing member updates their role and returns 200"() {
    given: "A owns a workspace where B is already a VIEWER"
    def (workspaceId, ownerId) = seedMembership(USER_A, 'OWNER')
    def userB = provisionUser(USER_B)
    addMember(workspaceId, userB.id, 'VIEWER')

    when: "A re-adds B as an editor"
    def updated = rest.exchange("${workspacesUrl()}/${workspaceId}/members", HttpMethod.POST,
      asUser(USER_A, new AddMemberRequest(employeeNumber: USER_B, role: WorkspaceRole.EDITOR)), WorkspaceMember)

    then: "the response is 200 (updated, not created) and the role changed"
    updated.statusCode == HttpStatus.OK
    updated.body.role == WorkspaceRole.EDITOR
    memberRepository.findByWorkspaceIdAndUserId(workspaceId, userB.id).get().role == 'EDITOR'
  }

  def "a non-owner cannot add a member (403)"() {
    given:
    def (workspaceId, _) = seedMembership(USER_A, 'EDITOR')

    expect:
    rest.exchange("${workspacesUrl()}/${workspaceId}/members", HttpMethod.POST,
      asUser(USER_A, new AddMemberRequest(employeeNumber: USER_B, role: WorkspaceRole.VIEWER)), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  def "adding a member with an invalid role is rejected with 400"() {
    given:
    def (workspaceId, _) = seedMembership(USER_A, 'OWNER')
    def body = '{"employeeNumber":"' + USER_B + '","role":"ADMIN"}'

    expect:
    rest.exchange("${workspacesUrl()}/${workspaceId}/members", HttpMethod.POST,
      new HttpEntity<>(body, headersFor(USER_A)), Map)
      .statusCode == HttpStatus.BAD_REQUEST
  }

  def "a non-member cannot list workspace members (403)"() {
    given:
    def (workspaceId, _) = seedMembership(USER_A, 'OWNER')

    expect:
    rest.exchange("${workspacesUrl()}/${workspaceId}/members", HttpMethod.GET, asUser(USER_C), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  def "an owner can remove a member, who then loses access"() {
    given:
    def (workspaceId, ownerId) = seedMembership(USER_A, 'OWNER')
    def userB = provisionUser(USER_B)
    addMember(workspaceId, userB.id, 'EDITOR')
    def seeded = persistNote(workspaceId, ownerId, 'shared work')

    when:
    def removed = rest.exchange("${workspacesUrl()}/${workspaceId}/members/${userB.id}",
      HttpMethod.DELETE, asUser(USER_A), Void)

    then:
    removed.statusCode == HttpStatus.NO_CONTENT
    memberRepository.findByWorkspaceIdAndUserId(workspaceId, userB.id).isEmpty()

    and: "B can no longer read notes in the workspace"
    rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.GET, asUser(USER_B), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  def "the last owner cannot be removed (403)"() {
    given:
    def (workspaceId, ownerId) = seedMembership(USER_A, 'OWNER')

    expect:
    rest.exchange("${workspacesUrl()}/${workspaceId}/members/${ownerId}", HttpMethod.DELETE, asUser(USER_A), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  // ---- note create / read ---------------------------------------------------------------------

  def "an editor can create a note in their workspace"() {
    given:
    def (workspaceId, userId) = seedMembership(USER_A, 'EDITOR')

    when:
    def response = rest.exchange(notesUrl(), HttpMethod.POST,
      asUser(USER_A, new CreateNoteRequest(workspaceId: workspaceId, title: 'Dock 12', content: 'Re-check wrap')), Note)

    then:
    response.statusCode == HttpStatus.CREATED
    response.body.id != null
    response.body.workspaceId == workspaceId
    response.body.createdByUserId == userId
    !response.body.completed

    and:
    def stored = noteRepository.findById(response.body.id).orElse(null)
    stored != null
    stored.workspaceId == workspaceId
  }

  def "a viewer cannot create a note (403)"() {
    given:
    def (workspaceId, _) = seedMembership(USER_A, 'VIEWER')

    expect:
    rest.exchange(notesUrl(), HttpMethod.POST,
      asUser(USER_A, new CreateNoteRequest(workspaceId: workspaceId, title: 'nope', content: 'x')), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  def "a non-member cannot create a note in a workspace (403)"() {
    given:
    def ws = seedWorkspace()

    expect:
    rest.exchange(notesUrl(), HttpMethod.POST,
      asUser(USER_C, new CreateNoteRequest(workspaceId: ws.id, title: 'nope', content: 'x')), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  def "any member can read a note in their workspace"() {
    given:
    def (workspaceId, ownerId) = seedMembership(USER_A, 'OWNER')
    def viewer = provisionUser(USER_B)
    addMember(workspaceId, viewer.id, 'VIEWER')
    def seeded = persistNote(workspaceId, ownerId, 'mine')

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.GET, asUser(USER_B), Note)

    then:
    response.statusCode == HttpStatus.OK
    response.body.id == seeded.id
  }

  def "reading a non-existent note returns 404"() {
    expect:
    rest.exchange("${notesUrl()}/999999", HttpMethod.GET, asUser(USER_A), Map)
      .statusCode == HttpStatus.NOT_FOUND
  }

  def "a non-member cannot read a note (403)"() {
    given:
    def (workspaceId, ownerId) = seedMembership(USER_A, 'OWNER')
    def seeded = persistNote(workspaceId, ownerId)

    expect:
    rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.GET, asUser(USER_C), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  // ---- update / complete (role-gated) ---------------------------------------------------------

  def "an editor can update a note"() {
    given:
    def (workspaceId, userId) = seedMembership(USER_A, 'EDITOR')
    def seeded = persistNote(workspaceId, userId, 'old title')

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.PUT,
      asUser(USER_A, new UpdateNoteRequest(title: 'new title', content: 'new body', completed: true)), Note)

    then:
    response.statusCode == HttpStatus.OK
    response.body.title == 'new title'
    response.body.completed
    noteRepository.findById(seeded.id).get().title == 'new title'
  }

  def "a viewer cannot update a note (403) and the data is unchanged"() {
    given:
    def (workspaceId, ownerId) = seedMembership(USER_A, 'OWNER')
    def viewer = provisionUser(USER_B)
    addMember(workspaceId, viewer.id, 'VIEWER')
    def seeded = persistNote(workspaceId, ownerId, 'immutable to viewers')

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.PUT,
      asUser(USER_B, new UpdateNoteRequest(title: 'hacked', content: 'x', completed: true)), Map)

    then:
    response.statusCode == HttpStatus.FORBIDDEN
    noteRepository.findById(seeded.id).get().title == 'immutable to viewers'
  }

  def "an editor can complete a note"() {
    given:
    def (workspaceId, userId) = seedMembership(USER_A, 'EDITOR')
    def seeded = persistNote(workspaceId, userId, 'to complete', false)

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}/complete", HttpMethod.POST, asUser(USER_A), Note)

    then:
    response.statusCode == HttpStatus.OK
    response.body.completed
    noteRepository.findById(seeded.id).get().completed
  }

  // ---- soft delete / trash / restore ----------------------------------------------------------

  def "deleting a note trashes it: it leaves the active list and appears in trash"() {
    given:
    def (workspaceId, userId) = seedMembership(USER_A, 'EDITOR')
    def seeded = persistNote(workspaceId, userId, 'to trash')

    when: "the note is deleted"
    def response = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.DELETE, asUser(USER_A), Void)

    then:
    response.statusCode == HttpStatus.NO_CONTENT
    noteRepository.findById(seeded.id).get().deletedAt != null

    and: "it no longer appears in the active list"
    def active = rest.exchange(notesUrl(), HttpMethod.GET, asUser(USER_A), NotesResponse)
    active.body.notes.isEmpty()

    and: "it appears in the trash listing"
    def trash = rest.exchange("${notesUrl()}/trash", HttpMethod.GET, asUser(USER_A), NotesResponse)
    trash.body.notes*.id == [seeded.id]
  }

  def "a trashed note can be restored"() {
    given:
    def (workspaceId, userId) = seedMembership(USER_A, 'EDITOR')
    def seeded = persistNote(workspaceId, userId, 'was trashed', false, Instant.now())

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}/restore", HttpMethod.POST, asUser(USER_A), Note)

    then:
    response.statusCode == HttpStatus.OK
    !response.body.deletedAt.isPresent()
    noteRepository.findById(seeded.id).get().deletedAt == null

    and: "it is active again"
    def active = rest.exchange(notesUrl(), HttpMethod.GET, asUser(USER_A), NotesResponse)
    active.body.notes*.id == [seeded.id]
  }

  def "a viewer cannot delete a note (403)"() {
    given:
    def (workspaceId, ownerId) = seedMembership(USER_A, 'OWNER')
    def viewer = provisionUser(USER_B)
    addMember(workspaceId, viewer.id, 'VIEWER')
    def seeded = persistNote(workspaceId, ownerId)

    expect:
    rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.DELETE, asUser(USER_B), Map)
      .statusCode == HttpStatus.FORBIDDEN
    noteRepository.findById(seeded.id).get().deletedAt == null
  }

  // ---- listing / search / pagination ----------------------------------------------------------

  def "list returns only active notes in workspaces the caller belongs to"() {
    given:
    def (wsA, userA) = seedMembership(USER_A, 'EDITOR')
    def foreign = seedWorkspace('foreign')
    def userC = provisionUser(USER_C)
    addMember(foreign.id, userC.id, 'OWNER')
    persistNote(wsA, userA, 'a-1')
    persistNote(wsA, userA, 'a-2')
    persistNote(wsA, userA, 'a-trashed', false, Instant.now())
    persistNote(foreign.id, userC.id, 'c-1')

    when:
    def response = rest.exchange(notesUrl(), HttpMethod.GET, asUser(USER_A), NotesResponse)

    then:
    response.body.notes*.title as Set == ['a-1', 'a-2'] as Set
  }

  def "list honours a case-insensitive title search term"() {
    given:
    def (workspaceId, userId) = seedMembership(USER_A, 'EDITOR')
    persistNote(workspaceId, userId, 'Dock 12 inspection')
    persistNote(workspaceId, userId, 'Trailer audit')

    when:
    def response = rest.exchange("${notesUrl()}?searchTerm=dock", HttpMethod.GET, asUser(USER_A), NotesResponse)

    then:
    response.body.notes*.title == ['Dock 12 inspection']
  }

  def "list can be filtered to a single workspace"() {
    given:
    def userA = provisionUser(USER_A)
    def ws1 = seedWorkspace('one')
    def ws2 = seedWorkspace('two')
    addMember(ws1.id, userA.id, 'EDITOR')
    addMember(ws2.id, userA.id, 'EDITOR')
    persistNote(ws1.id, userA.id, 'in one')
    persistNote(ws2.id, userA.id, 'in two')

    when:
    def response = rest.exchange("${notesUrl()}?workspaceId=${ws1.id}", HttpMethod.GET, asUser(USER_A), NotesResponse)

    then:
    response.body.notes*.title == ['in one']
  }

  def "list paginates results"() {
    given:
    def (workspaceId, userId) = seedMembership(USER_A, 'EDITOR')
    (1..5).each { persistNote(workspaceId, userId, "note-${it}") }

    when:
    def response = rest.exchange("${notesUrl()}?pageNumber=1&itemsPerPage=2", HttpMethod.GET, asUser(USER_A), NotesResponse)

    then:
    response.body.notes.size() == 2
    response.body.pageInfo.totalItems == 5
    response.body.pageInfo.pagesCount == 3
  }
}
