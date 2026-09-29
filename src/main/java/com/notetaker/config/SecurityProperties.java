package com.notetaker.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the {@code notetaker.security} configuration tree.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties("notetaker.security")
public class SecurityProperties {

  /** URIs that do not require authentication (actuator, swagger, h2-console, ...). */
  private String[] publicUris = new String[0];

  private String[] corsAllowedOrigins = {"*"};
  private String[] corsAllowedMethods = {"GET", "POST", "PUT", "PATCH", "DELETE"};

  /** JWT claim that carries the caller's user id; falls back to the token subject when absent. */
  private String userIdClaim = "employeeNumber";

  /** User id used on profiles that don't require a JWT. */
  private String defaultUserId = "local-user";

  /** Trusted token issuers keyed by a logical name (e.g. {@code notetaker-ui-issuer}, {@code test-issuer}). */
  private Map<String, IssuerProperties> tokenIssuers = new LinkedHashMap<>();

  /** JWKS (public-key) caching configuration shared by all issuers. */
  private JwksCache jwks = new JwksCache();

  /** Validated-token keychain: caches a token's validated result to skip re-validation. */
  private TokenCache tokenCache = new TokenCache();

  @Getter
  @Setter
  public static class IssuerProperties {
    /** The {@code iss} value / issuer URI a token must carry to be routed to this issuer. */
    private String url;
    /** Optional expected audience; when set it is enforced during validation. */
    private String audience;
    /** Path appended to {@link #url} to locate the JWKS document. */
    private String keysUri = "/v1/keys";
  }

  @Getter
  @Setter
  public static class JwksCache {
    /** How long cached JWKS keys stay fresh before a refresh is triggered. */
    private long keyCacheRefreshMinutes = 240;
    /** How long stale keys may still be served if the issuer is unreachable. */
    private long outageProtectionMinutes = 1440;
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 5_000;
  }

  @Getter
  @Setter
  public static class TokenCache {
    /** How long a validated token stays in the keychain before it must be re-validated. */
    private long ttlMinutes = 240;
    /** Maximum number of validated tokens held in the keychain. */
    private long maximumSize = 10_000;
  }
}
