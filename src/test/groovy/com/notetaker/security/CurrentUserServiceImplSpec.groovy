package com.notetaker.security

import com.notetaker.config.SecurityProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class CurrentUserServiceImplSpec extends Specification {

  SecurityProperties properties = new SecurityProperties(
    userIdClaim: 'employeeNumber',
    defaultUserId: 'local-user')

  @Subject
  CurrentUserServiceImpl service = new CurrentUserServiceImpl(properties)

  def cleanup() {
    SecurityContextHolder.clearContext()
    RequestContextHolder.resetRequestAttributes()
  }

  private static Jwt jwt(Map claims) {
    def builder = Jwt.withTokenValue('token')
      .header('alg', 'RS256')
      .issuedAt(Instant.now())
      .expiresAt(Instant.now().plusSeconds(60))
      .subject('subject-id')
    claims.each { k, v -> builder.claim(k as String, v) }
    return builder.build()
  }

  private void setJwt(Jwt token) {
    SecurityContextHolder.context.authentication = new JwtAuthenticationToken(token)
  }

  private void setRequestHeader(String name, String value) {
    def request = new MockHttpServletRequest()
    if (value != null) {
      request.addHeader(name, value)
    }
    RequestContextHolder.requestAttributes = new ServletRequestAttributes(request)
  }

  def "prefers the employeeNumber claim from the JWT"() {
    given:
    setJwt(jwt([employeeNumber: '987654', sub: 'subject-id']))

    expect:
    service.getCurrentUserId() == '987654'
  }

  def "falls back to the JWT subject when the claim is absent"() {
    given:
    setJwt(jwt([sub: 'subject-id']))

    expect:
    service.getCurrentUserId() == 'subject-id'
  }

  def "throws UserIdMissing when a JWT carries neither claim nor subject"() {
    given:
    def blankSub = Jwt.withTokenValue('t')
      .header('alg', 'RS256')
      .claim('sub', '')
      .build()
    SecurityContextHolder.context.authentication = new JwtAuthenticationToken(blankSub)

    when:
    service.getCurrentUserId()

    then:
    thrown(UserIdMissingException)
  }

  def "uses the X-Employee-Number header when no JWT is present (local bypass)"() {
    given:
    setRequestHeader(CurrentUserServiceImpl.DEMO_USER_HEADER, '424242')

    expect:
    service.getCurrentUserId() == '424242'
  }

  def "falls back to the configured default user when no JWT and no header"() {
    given:
    setRequestHeader(CurrentUserServiceImpl.DEMO_USER_HEADER, null)

    expect:
    service.getCurrentUserId() == 'local-user'
  }

  def "falls back to the default user when there is no request context at all"() {
    expect:
    service.getCurrentUserId() == 'local-user'
  }
}
