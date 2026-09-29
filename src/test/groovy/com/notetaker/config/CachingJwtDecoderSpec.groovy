package com.notetaker.config

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import spock.lang.Specification

import java.time.Instant

class CachingJwtDecoderSpec extends Specification {

  JwtDecoder delegate = Mock()
  def config = new SecurityProperties.TokenCache(ttlMinutes: 240, maximumSize: 100)
  def decoder = new CachingJwtDecoder(delegate, config)

  private static Jwt jwt(String value, Instant exp) {
    Jwt.withTokenValue(value)
      .header('alg', 'RS256')
      .claim('employeeNumber', '123')
      .issuedAt(Instant.now().minusSeconds(60))
      .expiresAt(exp)
      .build()
  }

  def "validates a token once and serves later requests from the keychain"() {
    given:
    def token = 'aaa.bbb.ccc'
    def valid = jwt(token, Instant.now().plusSeconds(3600))

    when:
    def first = decoder.decode(token)
    def second = decoder.decode(token)

    then: "the delegate is only hit on the first decode"
    1 * delegate.decode(token) >> valid
    first.is(valid)
    second.is(valid)
  }

  def "re-validates when the cached token has already expired"() {
    given:
    def token = 'expired.token.value'
    def expired = jwt(token, Instant.now().minusSeconds(1))

    when:
    decoder.decode(token)
    decoder.decode(token)

    then: "an expired keychain entry is never honored - the delegate runs again"
    2 * delegate.decode(token) >> expired
  }

  def "caches each distinct token independently"() {
    when:
    decoder.decode('token-one')
    decoder.decode('token-two')

    then:
    1 * delegate.decode('token-one') >> jwt('token-one', Instant.now().plusSeconds(3600))
    1 * delegate.decode('token-two') >> jwt('token-two', Instant.now().plusSeconds(3600))
  }
}
