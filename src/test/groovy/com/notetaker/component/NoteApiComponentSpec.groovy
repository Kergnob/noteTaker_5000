package com.notetaker.component

import com.notetaker.model.CreateNoteRequest
import com.notetaker.model.Note
import com.notetaker.model.NoteShare
import com.notetaker.model.NoteSharesResponse
import com.notetaker.model.NotesResponse
import com.notetaker.model.ShareNoteRequest
import com.notetaker.model.SharePermission
import com.notetaker.model.UpdateNoteRequest
import com.notetaker.notes.v1.repository.NoteRepository
import com.notetaker.notes.v1.repository.NoteShareRepository
import com.notetaker.notes.v1.repository.entity.NoteEntity
import com.notetaker.notes.v1.repository.entity.NoteShareEntity
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
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

import java.util.UUID

/**
 * Component test: boots the whole application on a random port under the {@code component-test}
 * profile (no JWT required), drives it exclusively through the HTTP API, and uses the JPA
 * repositories / H2 database directly for data preparation and verification.
 */
@ActiveProfiles('component-test')
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NoteApiComponentSpec extends Specification {

  static final String USER_A = 'A-1001'
  static final String USER_B = 'B-2002'
  static final String USER_C = 'C-3003'

  @LocalServerPort
  int port

  @Autowired
  TestRestTemplate rest

  @Autowired
  NoteRepository noteRepository

  @Autowired
  NoteShareRepository noteShareRepository

  def setup() {
    noteShareRepository.deleteAll()
    noteRepository.deleteAll()
  }

  private String notesUrl() { "http://localhost:${port}/api/v1/notes" }

  private static HttpHeaders headersFor(String user) {
    def headers = new HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    if (user != null) {
      headers.set(CurrentUserServiceImpl.DEMO_USER_HEADER, user)
    }
    return headers
  }

  private <T> HttpEntity<T> asUser(String user, T body = null) {
    new HttpEntity<>(body, headersFor(user))
  }

  private NoteEntity persistNote(String owner, String title = 'seeded', boolean completed = false) {
    def n = new NoteEntity()
    n.id = UUID.randomUUID().toString()
    n.ownerId = owner
    n.title = title
    n.content = 'seed body'
    n.completed = completed
    return noteRepository.saveAndFlush(n)
  }

  // ---- create / read happy paths -------------------------------------------------------------

  def "a user can create a note and it is persisted with them as owner"() {
    when:
    def response = rest.exchange(notesUrl(), HttpMethod.POST,
      asUser(USER_A, new CreateNoteRequest(title: 'Dock 12', content: 'Re-check wrap')), Note)

    then:
    response.statusCode == HttpStatus.CREATED
    response.body.id != null
    response.body.ownerId == USER_A
    response.body.title == 'Dock 12'
    !response.body.completed
    !response.body.shared

    and: "it exists in the database"
    def stored = noteRepository.findById(response.body.id).orElse(null)
    stored != null
    stored.ownerId == USER_A
  }

  def "the owner can read their own note (shared=false)"() {
    given:
    def seeded = persistNote(USER_A, 'mine')

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.GET, asUser(USER_A), Note)

    then:
    response.statusCode == HttpStatus.OK
    response.body.id == seeded.id
    !response.body.shared
  }

  def "reading a non-existent note returns 404"() {
    expect:
    rest.exchange("${notesUrl()}/does-not-exist", HttpMethod.GET, asUser(USER_A), Map)
      .statusCode == HttpStatus.NOT_FOUND
  }

  def "a user who is neither owner nor sharee cannot read the note (403)"() {
    given:
    def seeded = persistNote(USER_A)

    expect:
    rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.GET, asUser(USER_B), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  // ---- sharing --------------------------------------------------------------------------------

  def "the owner can share a note and the sharee can then read it (shared=true)"() {
    given:
    def seeded = persistNote(USER_A, 'to share')

    when: "owner shares with B"
    def shareResponse = rest.exchange("${notesUrl()}/${seeded.id}/shares", HttpMethod.POST,
      asUser(USER_A, new ShareNoteRequest(sharedWithUserId: USER_B, permission: SharePermission.READ)), NoteShare)

    then:
    shareResponse.statusCode == HttpStatus.CREATED
    shareResponse.body.sharedWithUserId == USER_B
    shareResponse.body.permission == SharePermission.READ
    noteShareRepository.findByNoteId(seeded.id).size() == 1

    when: "B reads the shared note"
    def getResponse = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.GET, asUser(USER_B), Note)

    then:
    getResponse.statusCode == HttpStatus.OK
    getResponse.body.shared
  }

  def "a shared note shows up in the sharee's list flagged as shared"() {
    given:
    def owned = persistNote(USER_B, 'b owns this')
    def shared = persistNote(USER_A, 'a shares this')
    noteShareRepository.saveAndFlush(new NoteShareEntity(shared.id, USER_B, 'READ'))

    when:
    def response = rest.exchange(notesUrl(), HttpMethod.GET, asUser(USER_B), NotesResponse)

    then:
    response.statusCode == HttpStatus.OK
    response.body.notes*.id as Set == [owned.id, shared.id] as Set
    def sharedNote = response.body.notes.find { it.id == shared.id }
    def ownedNote = response.body.notes.find { it.id == owned.id }
    sharedNote.shared
    !ownedNote.shared
    response.body.pageInfo.totalItems == 2
  }

  def "the owner can list, unshare, and the sharee then loses access"() {
    given:
    def seeded = persistNote(USER_A)
    noteShareRepository.saveAndFlush(new NoteShareEntity(seeded.id, USER_B, 'READ'))

    when: "owner lists shares"
    def shares = rest.exchange("${notesUrl()}/${seeded.id}/shares", HttpMethod.GET, asUser(USER_A), NoteSharesResponse)

    then:
    shares.body.shares.size() == 1

    when: "owner unshares B"
    def unshare = rest.exchange("${notesUrl()}/${seeded.id}/shares/${USER_B}", HttpMethod.DELETE, asUser(USER_A), Void)

    then:
    unshare.statusCode == HttpStatus.NO_CONTENT
    noteShareRepository.findByNoteId(seeded.id).isEmpty()

    and: "B can no longer read the note"
    rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.GET, asUser(USER_B), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  def "sharing with an invalid permission is rejected with 400"() {
    given:
    def seeded = persistNote(USER_A)
    def body = '{"sharedWithUserId":"' + USER_B + '","permission":"ADMIN"}'
    def headers = headersFor(USER_A)

    expect:
    rest.exchange("${notesUrl()}/${seeded.id}/shares", HttpMethod.POST, new HttpEntity<>(body, headers), Map)
      .statusCode == HttpStatus.BAD_REQUEST
  }

  def "a non-owner cannot share a note (403)"() {
    given:
    def seeded = persistNote(USER_A)

    expect:
    rest.exchange("${notesUrl()}/${seeded.id}/shares", HttpMethod.POST,
      asUser(USER_B, new ShareNoteRequest(sharedWithUserId: USER_C, permission: SharePermission.READ)), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  // ---- update / complete / delete (owner-only) ------------------------------------------------

  def "the owner can update their note"() {
    given:
    def seeded = persistNote(USER_A, 'old title')

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.PUT,
      asUser(USER_A, new UpdateNoteRequest(title: 'new title', content: 'new body', completed: true)), Note)

    then:
    response.statusCode == HttpStatus.OK
    response.body.title == 'new title'
    response.body.completed

    and:
    def stored = noteRepository.findById(seeded.id).get()
    stored.title == 'new title'
    stored.completed
  }

  def "a non-owner cannot update the note (403) and the data is unchanged"() {
    given:
    def seeded = persistNote(USER_A, 'immutable to others')

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.PUT,
      asUser(USER_B, new UpdateNoteRequest(title: 'hacked', content: 'x', completed: true)), Map)

    then:
    response.statusCode == HttpStatus.FORBIDDEN
    noteRepository.findById(seeded.id).get().title == 'immutable to others'
  }

  def "the owner can complete a note"() {
    given:
    def seeded = persistNote(USER_A, 'to complete', false)

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}/complete", HttpMethod.POST, asUser(USER_A), Note)

    then:
    response.statusCode == HttpStatus.OK
    response.body.completed
    noteRepository.findById(seeded.id).get().completed
  }

  def "a non-owner cannot complete a note (403)"() {
    given:
    def seeded = persistNote(USER_A)

    expect:
    rest.exchange("${notesUrl()}/${seeded.id}/complete", HttpMethod.POST, asUser(USER_B), Map)
      .statusCode == HttpStatus.FORBIDDEN
  }

  def "deleting a note as owner removes it and cascades its shares"() {
    given:
    def seeded = persistNote(USER_A)
    noteShareRepository.saveAndFlush(new NoteShareEntity(seeded.id, USER_B, 'READ'))

    when:
    def response = rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.DELETE, asUser(USER_A), Void)

    then:
    response.statusCode == HttpStatus.NO_CONTENT
    noteRepository.findById(seeded.id).isEmpty()
    noteShareRepository.findByNoteId(seeded.id).isEmpty()
  }

  def "a non-owner cannot delete a note (403)"() {
    given:
    def seeded = persistNote(USER_A)

    expect:
    rest.exchange("${notesUrl()}/${seeded.id}", HttpMethod.DELETE, asUser(USER_B), Map)
      .statusCode == HttpStatus.FORBIDDEN
    noteRepository.findById(seeded.id).isPresent()
  }

  // ---- listing / search -----------------------------------------------------------------------

  def "list returns only notes visible to the caller"() {
    given:
    persistNote(USER_A, 'a-1')
    persistNote(USER_A, 'a-2')
    persistNote(USER_C, 'c-1')

    when:
    def response = rest.exchange(notesUrl(), HttpMethod.GET, asUser(USER_A), NotesResponse)

    then:
    response.body.notes.size() == 2
    response.body.notes.every { it.ownerId == USER_A }
  }

  def "list honours a case-insensitive title search term"() {
    given:
    persistNote(USER_A, 'Dock 12 inspection')
    persistNote(USER_A, 'Trailer audit')

    when:
    def response = rest.exchange("${notesUrl()}?searchTerm=dock", HttpMethod.GET, asUser(USER_A), NotesResponse)

    then:
    response.body.notes.size() == 1
    response.body.notes[0].title == 'Dock 12 inspection'
  }

  def "list paginates results"() {
    given:
    (1..5).each { persistNote(USER_A, "note-${it}") }

    when:
    def response = rest.exchange("${notesUrl()}?pageNumber=1&itemsPerPage=2", HttpMethod.GET, asUser(USER_A), NotesResponse)

    then:
    response.body.notes.size() == 2
    response.body.pageInfo.totalItems == 5
    response.body.pageInfo.pagesCount == 3
  }
}
