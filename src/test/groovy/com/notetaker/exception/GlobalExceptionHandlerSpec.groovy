package com.notetaker.exception

import com.notetaker.notes.v1.exception.NoteAccessDeniedException
import com.notetaker.notes.v1.exception.NoteNotFoundException
import com.notetaker.security.UserIdMissingException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.core.AuthenticationException
import spock.lang.Specification
import spock.lang.Subject

class GlobalExceptionHandlerSpec extends Specification {

  @Subject
  GlobalExceptionHandler handler = new GlobalExceptionHandler()

  def "bad-request family maps to 400 with populated ErrorResponse"() {
    when:
    def body = handler.handleBadRequest(new IllegalArgumentException('bad input'))

    then:
    body.statusCode == 400
    body.error == 'BAD_REQUEST'
    body.message == 'bad input'
    body.timestamp != null
    body.requestId != null
  }

  def "note-not-found maps to 404"() {
    when:
    def body = handler.handleNotFound(new NoteNotFoundException('n1'))

    then:
    body.statusCode == 404
    body.error == 'NOT_FOUND'
    body.message == 'Note n1 not found'
  }

  def "note-access-denied maps to 403"() {
    when:
    def body = handler.handleForbidden(new NoteAccessDeniedException('nope'))

    then:
    body.statusCode == 403
    body.error == 'FORBIDDEN'
  }

  def "authentication failures map to 401"() {
    when:
    def body = handler.handleUnauthorized(exception)

    then:
    body.statusCode == 401
    body.error == 'UNAUTHORIZED'

    where:
    exception << [new UserIdMissingException(), new StubAuthException('no token')]
  }

  def "optimistic-locking failures map to 409"() {
    when:
    def body = handler.handleConflict(new ObjectOptimisticLockingFailureException(Object, 'x'))

    then:
    body.statusCode == 409
    body.error == 'CONFLICT'
  }

  def "unexpected errors map to 500"() {
    when:
    def body = handler.handleInternalServerError(new RuntimeException('boom'))

    then:
    body.statusCode == 500
    body.error == 'INTERNAL_SERVER_ERROR'
  }

  static class StubAuthException extends AuthenticationException {
    StubAuthException(String msg) { super(msg) }
  }
}
