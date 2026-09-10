package com.dedeepya.agent.security;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtPolicyTest {
  Jwt token(String issuer, String audience, Instant expiry) {
    return Jwt.withTokenValue("test")
        .header("alg", "RS256")
        .issuer(issuer)
        .subject("user1")
        .audience(List.of(audience))
        .issuedAt(Instant.now().minusSeconds(600))
        .expiresAt(expiry)
        .build();
  }

  @Test
  void issuerAudienceAndExpiryAreAllRequired() {
    var validator =
        SecurityConfiguration.issuerAudienceValidator("https://identity.example", "agents");
    assertThat(
            validator
                .validate(
                    token("https://identity.example", "agents", Instant.now().plusSeconds(300)))
                .hasErrors())
        .isFalse();
    assertThat(
            validator
                .validate(
                    token("https://attacker.example", "agents", Instant.now().plusSeconds(300)))
                .hasErrors())
        .isTrue();
    assertThat(
            validator
                .validate(
                    token(
                        "https://identity.example", "another-api", Instant.now().plusSeconds(300)))
                .hasErrors())
        .isTrue();
    assertThat(
            validator
                .validate(
                    token("https://identity.example", "agents", Instant.now().minusSeconds(120)))
                .hasErrors())
        .isTrue();
  }

  @Test
  void identityWithoutTenantCannotEnterBusinessFlow() {
    var token = token("https://identity.example", "agents", Instant.now().plusSeconds(300));
    assertThatThrownBy(() -> Actor.from(new JwtAuthenticationToken(token)))
        .hasMessageContaining("tenant/subject");
  }
}
