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
import com.notetaker.notes.v1.repository.AppUserRepository
import com.notetaker.notes.v1.repository.NoteRepository
import com.notetaker.notes.v1.repository.WorkspaceMemberRepository
import com.notetaker.notes.v1.repository.WorkspaceRepository
import com.notetaker.security.CurrentUserServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification

/**
 * End-to-end collaboration journeys: boots the whole application on a random port under the
 * {@code component-test} profile (no JWT required) and drives multiple users through the HTTP API.
 *
 * Unlike {@link NoteApiComponentSpec} (which isolates single operations), each test here walks a
 * realistic multi-step story across several collaborators. "Existing" users are enriched directly
 * into the APP_USER table with SQL before the journey starts, so the workspace owner onboards
 * accounts that already exist rather than freshly provisioned ones.
 */
@ActiveProfiles('component-test')
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NoteCollaborationJourneySpec extends Specification {

  static final String ANNA = 'E-100'
  static final String BROOKE = 'E-200'
  static final String CARLOS = 'E-300'

  @LocalServerPort
  int port

  @Autowired
  TestRestTemplate rest

  @Autowired
  JdbcTemplate jdbc

  @Autowired
  PlatformTransactionManager txManager

  @Autowired
  NoteRepository noteRepository

  @Autowired
  WorkspaceRepository workspaceRepository

  @Autowired
  WorkspaceMemberRepository memberRepository

  @Autowired
  AppUserRepository appUserRepository

  def setup() {
    noteRepository.deleteAll()
    memberRepository.deleteAll()
    workspaceRepository.deleteAll()
    appUserRepository.deleteAll()
  }

  private String notesUrl() { "http://localhost:${port}/api/v1/notes" }

  private String workspacesUrl() { "http://localhost:${port}/api/v1/workspaces" }

  private static HttpHeaders headersFor(String user) {
    def headers = new HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    headers.set(CurrentUserServiceImpl.DEMO_USER_HEADER, user)
    return headers
  }

  private <T> HttpEntity<T> asUser(String user, T body = null) {
    new HttpEntity<>(body, headersFor(user))
  }

  /**
   * Enriches APP_USER with a pre-existing account and returns its generated USER_ID. The write is
   * wrapped in an explicit transaction so it commits (the pool runs with auto-commit disabled) and
   * is therefore visible to the application running on the server thread.
   */
  private long seedExistingUser(String emp, String email, String displayName) {
    new TransactionTemplate(txManager).execute {
      jdbc.update('''INSERT INTO NOTETAKER.APP_USER
          (EMP_NBR, EMAIL_TXT, DSPL_NM, REC_CRTN_TMSTP, REC_MODIFY_TMSTP)
          VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''', emp, email, displayName)
      jdbc.queryForObject('SELECT USER_ID FROM NOTETAKER.APP_USER WHERE EMP_NBR = ?', Long, emp)
    }
  }

  private long createWorkspace(String owner, String name) {
    def created = rest.exchange(workspacesUrl(), HttpMethod.POST,
      asUser(owner, new CreateWorkspaceRequest(name: name)), Workspace)
    assert created.statusCode == HttpStatus.CREATED
    return created.body.id
  }

  private WorkspaceMember addMember(String owner, long workspaceId, String emp, WorkspaceRole role) {
    rest.exchange("${workspacesUrl()}/${workspaceId}/members", HttpMethod.POST,
      asUser(owner, new AddMemberRequest(employeeNumber: emp, role: role)), WorkspaceMember).body
  }

  private Note createNote(String author, long workspaceId, String title, String content = 'body') {
    def created = rest.exchange(notesUrl(), HttpMethod.POST,
      asUser(author, new CreateNoteRequest(workspaceId: workspaceId, title: title, content: content)), Note)
    assert created.statusCode == HttpStatus.CREATED
    return created.body
  }

  private NotesResponse listNotes(String user) {
    rest.exchange(notesUrl(), HttpMethod.GET, asUser(user), NotesResponse).body
  }

  def "an owner onboards two existing users who each add notes that everyone can see"() {
    given: "Brooke and Carlos already exist in the directory"
    def brookeId = seedExistingUser(BROOKE, 'brooke@depot.example', 'Brooke Ramos')
    def carlosId = seedExistingUser(CARLOS, 'carlos@depot.example', 'Carlos Nunez')

    when: "Anna logs in and creates a workspace"
    def workspaceId = createWorkspace(ANNA, 'Dock 12 crew')

    and: "Anna adds both existing users as editors"
    def addedBrooke = addMember(ANNA, workspaceId, BROOKE, WorkspaceRole.EDITOR)
    def addedCarlos = addMember(ANNA, workspaceId, CARLOS, WorkspaceRole.EDITOR)

    then: "they were resolved to the pre-existing accounts, not re-provisioned"
    addedBrooke.role == WorkspaceRole.EDITOR
    addedCarlos.role == WorkspaceRole.EDITOR
    appUserRepository.findByEmployeeNumber(BROOKE).get().id == brookeId
    appUserRepository.findByEmployeeNumber(BROOKE).get().email == 'brooke@depot.example'
    appUserRepository.findByEmployeeNumber(CARLOS).get().id == carlosId

    and: "the workspace now has all three collaborators"
    def members = rest.exchange("${workspacesUrl()}/${workspaceId}/members", HttpMethod.GET,
      asUser(ANNA), WorkspaceMembersResponse).body
    members.members*.employeeNumber as Set == [ANNA, BROOKE, CARLOS] as Set

    when: "Brooke and Carlos each add a note"
    def brookeNote = createNote(BROOKE, workspaceId, "Brooke's inspection")
    def carlosNote = createNote(CARLOS, workspaceId, "Carlos's audit")

    then: "each note records its true author"
    brookeNote.createdByUserId == brookeId
    carlosNote.createdByUserId == carlosId

    and: "Anna, the owner, sees both collaborators' notes"
    listNotes(ANNA).notes*.title as Set == ["Brooke's inspection", "Carlos's audit"] as Set

    and: "each collaborator can read the other's note"
    rest.exchange("${notesUrl()}/${carlosNote.id}", HttpMethod.GET, asUser(BROOKE), Note)
      .body.title == "Carlos's audit"
    rest.exchange("${notesUrl()}/${brookeNote.id}", HttpMethod.GET, asUser(CARLOS), Note)
      .body.title == "Brooke's inspection"
  }

  def "an edit made by one collaborator is immediately visible to another"() {
    given: "Anna owns a workspace with Brooke (an existing user) as editor"
    seedExistingUser(BROOKE, 'brooke@depot.example', 'Brooke Ramos')
    def workspaceId = createWorkspace(ANNA, 'Trailer audits')
    addMember(ANNA, workspaceId, BROOKE, WorkspaceRole.EDITOR)

    and: "Brooke drafts a note"
    def note = createNote(BROOKE, workspaceId, 'Draft: seal check', 'todo')

    when: "Anna revises and completes it"
    def updated = rest.exchange("${notesUrl()}/${note.id}", HttpMethod.PUT,
      asUser(ANNA, new UpdateNoteRequest(title: 'Seal check complete', content: 'all sealed', completed: true)), Note)

    then:
    updated.statusCode == HttpStatus.OK

    and: "Brooke, coming back in, sees Anna's changes"
    def seenByBrooke = rest.exchange("${notesUrl()}/${note.id}", HttpMethod.GET, asUser(BROOKE), Note).body
    seenByBrooke.title == 'Seal check complete'
    seenByBrooke.content == 'all sealed'
    seenByBrooke.completed

    and: "the revised title also shows in Brooke's list"
    listNotes(BROOKE).notes*.title == ['Seal check complete']
  }

  def "a note deleted by one collaborator disappears from another collaborator's list"() {
    given: "Anna owns a workspace with Carlos (an existing user) as editor"
    seedExistingUser(CARLOS, 'carlos@depot.example', 'Carlos Nunez')
    def workspaceId = createWorkspace(ANNA, 'Loading notes')
    addMember(ANNA, workspaceId, CARLOS, WorkspaceRole.EDITOR)

    and: "Anna posts a note that Carlos can initially see"
    def note = createNote(ANNA, workspaceId, 'Pallet 7 damaged')
    assert listNotes(CARLOS).notes*.id == [note.id]

    when: "Anna deletes the note"
    def deleted = rest.exchange("${notesUrl()}/${note.id}", HttpMethod.DELETE, asUser(ANNA), Void)

    then:
    deleted.statusCode == HttpStatus.NO_CONTENT

    and: "Carlos, coming back in, no longer sees it in the active list"
    listNotes(CARLOS).notes.isEmpty()

    and: "but it is recoverable from the shared trash"
    def trash = rest.exchange("${notesUrl()}/trash", HttpMethod.GET, asUser(CARLOS), NotesResponse).body
    trash.notes*.id == [note.id]
  }
}
