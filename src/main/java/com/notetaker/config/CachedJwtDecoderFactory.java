package com.notetaker.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CachedJwtDecoderFactory {

  private final SecurityProperties properties;

  public CachedJwtDecoderFactory(SecurityProperties properties) {
    this.properties = properties;
  }

  public NimbusJwtDecoder create(SecurityProperties.IssuerProperties issuer) {
    try {
      SecurityProperties.JwksCache jwks = properties.getJwks();
      ResourceRetriever retriever =
          new DefaultResourceRetriever(jwks.getConnectTimeoutMs(), jwks.getReadTimeoutMs());
      String jwksUri = issuer.getUrl() + issuer.getKeysUri();
      long refreshMillis = jwks.getKeyCacheRefreshMinutes() * 60_000L;
      long outageMillis = jwks.getOutageProtectionMinutes() * 60_000L;

      JWKSource<SecurityContext> jwkSource = JWKSourceBuilder.<SecurityContext>create(
              new URL(jwksUri), retriever)
          .cache(refreshMillis, JWKSourceBuilder.DEFAULT_CACHE_REFRESH_TIMEOUT)
          .refreshAheadCache(true)
          .rateLimited(false)
          .retrying(true)
          .outageTolerant(outageMillis)
          .build();

      DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
      processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
          Set.of(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512), jwkSource));

      NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
      decoder.setJwtValidator(jwtValidator(issuer));
      return decoder;
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Failed to configure cached JWKS decoder", e);
    }
  }

  private OAuth2TokenValidator<Jwt> jwtValidator(SecurityProperties.IssuerProperties issuer) {
    OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(issuer.getUrl());
    if (!StringUtils.hasText(issuer.getAudience())) {
      return defaultValidator;
    }

    OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
      if (jwt.getAudience().contains(issuer.getAudience())) {
        return OAuth2TokenValidatorResult.success();
      }
      OAuth2Error error = new OAuth2Error(
          OAuth2ErrorCodes.INVALID_TOKEN,
          "The required audience is missing",
          null);
      return OAuth2TokenValidatorResult.failure(error);
    };

    return new DelegatingOAuth2TokenValidator<>(List.of(defaultValidator, audienceValidator));
  }
}
