package com.notetaker.security;

import com.notetaker.config.SecurityProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class CurrentUserServiceImpl implements CurrentUserService {

  /** Header used to impersonate a user id when no JWT is present. */
  public static final String DEMO_USER_HEADER = "X-Employee-Number";

  private final SecurityProperties properties;

  public CurrentUserServiceImpl(SecurityProperties properties) {
    this.properties = properties;
  }

  @Override
  public String getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken token) {
      Object claim = token.getToken().getClaim(properties.getUserIdClaim());
      if (claim != null && StringUtils.hasText(claim.toString())) {
        return claim.toString();
      }
      if (StringUtils.hasText(token.getName())) {
        return token.getName();
      }
      // A JWT was presented but carries no usable user id.
      throw new UserIdMissingException();
    }

    // No JWT present: use the impersonation header if set, otherwise the configured default user.
    String headerUser = headerUserId();
    if (StringUtils.hasText(headerUser)) {
      return headerUser;
    }
    return properties.getDefaultUserId();
  }

  private String headerUserId() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return attrs.getRequest().getHeader(DEMO_USER_HEADER);
    }
    return null;
  }
}
