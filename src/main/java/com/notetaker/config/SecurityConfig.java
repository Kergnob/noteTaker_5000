package com.notetaker.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final HandlerExceptionResolver handlerExceptionResolver;
  private final SecurityProperties properties;
  private final CachedJwtDecoderFactory decoderFactory;

  public SecurityConfig(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
      SecurityProperties properties,
      CachedJwtDecoderFactory decoderFactory) {
    this.handlerExceptionResolver = handlerExceptionResolver;
    this.properties = properties;
    this.decoderFactory = decoderFactory;
  }

  @Bean
  public AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {
    Map<String, AuthenticationManager> managers = new LinkedHashMap<>();
    for (SecurityProperties.IssuerProperties issuer : properties.getTokenIssuers().values()) {
      NimbusJwtDecoder decoder = decoderFactory.create(issuer);
      JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
      managers.put(issuer.getUrl(), new ProviderManager(provider));
    }
    return new JwtIssuerAuthenticationManagerResolver(
        (AuthenticationManagerResolver<String>) issuer -> managers.get(issuer));
  }

  /**
   * Local / demo profiles: no JWT required. This is the "bypass" - it lets us run and demo
   * the service (and the H2 console) without an OKTA token. Deployed profiles fall through to
   * {@link #defaultSecurityFilterChain} which requires a valid UI-issued JWT.
   */
  @Bean
  @Order(1)
  @Profile({"local", "component-test"})
  public SecurityFilterChain localPermitAllFilterChain(HttpSecurity http) throws Exception {
    return http
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
        .build();
  }

  @Bean
  @Order(2)
  @Profile("!local & !component-test")
  public SecurityFilterChain defaultSecurityFilterChain(
      HttpSecurity http,
      AuthenticationManagerResolver<HttpServletRequest> resolver) throws Exception {
    return http
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(properties.getPublicUris()).permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(resolver))
        .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
            (request, response, authException) -> handlerExceptionResolver.resolveException(
                request, response, null, authException)))
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(properties.getCorsAllowedOrigins()));
    configuration.setAllowedMethods(Arrays.asList(properties.getCorsAllowedMethods()));
    configuration.setAllowedHeaders(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
