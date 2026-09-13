package com.dedeepya.agent.security;

import com.dedeepya.agent.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record Actor(String tenant, String subject, boolean approver) {
  public static Actor from(Authentication auth) {
    if (auth instanceof JwtAuthenticationToken jwt) {
      String tenant = jwt.getToken().getClaimAsString("tenant_id");
      String subject = jwt.getToken().getSubject();
      if (tenant == null
          || !tenant.matches("[a-zA-Z0-9_-]{1,80}")
          || subject == null
          || subject.length() > 200)
        throw new ApiException(
            HttpStatus.FORBIDDEN, "INVALID_IDENTITY", "Missing or invalid tenant/subject claims");
      return new Actor(
          tenant,
          subject,
          auth.getAuthorities().stream()
              .anyMatch(a -> a.getAuthority().equals("SCOPE_agent:approve")));
    }
    // Only the explicit local security profile creates these authenticated users.
    return new Actor(
        "demo",
        auth.getName(),
        auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_APPROVER")));
  }
}
