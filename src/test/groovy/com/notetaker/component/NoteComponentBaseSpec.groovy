package com.notetaker.component

import com.notetaker.notes.v1.repository.AppUserRepository
import com.notetaker.notes.v1.repository.NoteRepository
import com.notetaker.notes.v1.repository.WorkspaceMemberRepository
import com.notetaker.notes.v1.repository.WorkspaceRepository
import com.notetaker.notes.v1.repository.entity.AppUserEntity
import com.notetaker.notes.v1.repository.entity.NoteEntity
import com.notetaker.notes.v1.repository.entity.WorkspaceEntity
import com.notetaker.notes.v1.repository.entity.WorkspaceMemberEntity
import com.notetaker.security.CurrentUserServiceImpl
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification

@ActiveProfiles('component-test')
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class NoteComponentBaseSpec extends Specification {

  @LocalServerPort
  protected int port

  @Autowired
  protected TestRestTemplate rest

  @Autowired
  protected NoteRepository noteRepository

  @Autowired
  protected WorkspaceRepository workspaceRepository

  @Autowired
  protected WorkspaceMemberRepository memberRepository

  @Autowired
  protected AppUserRepository appUserRepository

  @Autowired
  protected JdbcTemplate jdbc

  @Autowired
  protected PlatformTransactionManager txManager

  def setup() {
    noteRepository.deleteAll()
    memberRepository.deleteAll()
    workspaceRepository.deleteAll()
    appUserRepository.deleteAll()
  }

  protected String notesUrl() { "http://localhost:${port}/api/v1/notes" }

  protected String workspacesUrl() { "http://localhost:${port}/api/v1/workspaces" }

  protected static HttpHeaders headersFor(String user) {
    def headers = new HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    if (user != null) {
      headers.set(CurrentUserServiceImpl.DEMO_USER_HEADER, user)
    }
    headers
  }

  protected <T> HttpEntity<T> asUser(String user, T body = null) {
    new HttpEntity<>(body, headersFor(user))
  }

  protected AppUserEntity provisionUser(String employeeNumber) {
    appUserRepository.findByEmployeeNumber(employeeNumber).orElseGet {
      def user = new AppUserEntity()
      user.employeeNumber = employeeNumber
      user.email = "${employeeNumber}@notetaker.local"
      user.displayName = employeeNumber
      appUserRepository.saveAndFlush(user)
    }
  }

  protected WorkspaceEntity seedWorkspace(String name = 'crew') {
    def workspace = new WorkspaceEntity()
    workspace.name = name
    workspaceRepository.saveAndFlush(workspace)
  }

  protected void addMember(long workspaceId, long userId, String role) {
    memberRepository.saveAndFlush(new WorkspaceMemberEntity(workspaceId, userId, role))
  }

  protected List seedMembership(String employeeNumber, String role) {
    def user = provisionUser(employeeNumber)
    def workspace = seedWorkspace()
    addMember(workspace.id, user.id, role)
    [workspace.id, user.id]
  }

  protected NoteEntity persistNote(long workspaceId, long creatorId, String title = 'seeded',
                                   boolean completed = false, Instant deletedAt = null) {
    def note = new NoteEntity()
    note.workspaceId = workspaceId
    note.createdByUserId = creatorId
    note.title = title
    note.content = 'seed body'
    note.completed = completed
    note.deletedAt = deletedAt
    noteRepository.saveAndFlush(note)
  }

  protected long seedExistingUser(String employeeNumber, String email, String displayName) {
    new TransactionTemplate(txManager).execute {
      jdbc.update('''INSERT INTO NOTETAKER.APP_USER
          (EMP_NBR, EMAIL_TXT, DSPL_NM, REC_CRTN_TMSTP, REC_MODIFY_TMSTP)
          VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''', employeeNumber, email, displayName)
      jdbc.queryForObject('SELECT USER_ID FROM NOTETAKER.APP_USER WHERE EMP_NBR = ?', Long, employeeNumber)
    }
  }
}