package com.notetaker.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * A validated-token "keychain": validates a JWT once via the delegate decoder, then serves the
 * validated result from an in-memory cache for a configurable TTL, so repeated requests with the
 * same token do not re-run signature/claims validation.
 *
 * <p>The cache is keyed by a SHA-256 fingerprint of the token so raw bearer credentials are not
 * retained as map keys. A cached entry that has passed its own {@code exp} is treated as a miss
 * and re-validated, so an expired token is never served from the keychain.</p>
 */
public class CachingJwtDecoder implements JwtDecoder {

  private final JwtDecoder delegate;
  private final Cache<String, Jwt> keychain;

  public CachingJwtDecoder(JwtDecoder delegate, SecurityProperties.TokenCache config) {
    this.delegate = delegate;
    this.keychain = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(config.getTtlMinutes()))
        .maximumSize(config.getMaximumSize())
        .build();
  }

  @Override
  public Jwt decode(String token) throws JwtException {
    String key = fingerprint(token);
    Jwt cached = keychain.getIfPresent(key);
    if (cached != null && !isExpired(cached)) {
      return cached;
    }
    Jwt jwt = delegate.decode(token); // full validation: signature, issuer, expiry, audience
    keychain.put(key, jwt);
    return jwt;
  }

  private static boolean isExpired(Jwt jwt) {
    Instant exp = jwt.getExpiresAt();
    return exp != null && !exp.isAfter(Instant.now());
  }

  private static String fingerprint(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
