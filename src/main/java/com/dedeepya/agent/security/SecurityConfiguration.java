package com.dedeepya.agent.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
  private HttpSecurity base(HttpSecurity http) throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable());
  }

  @Bean
  @Profile("!local & !test")
  SecurityFilterChain production(HttpSecurity http) throws Exception {
    return base(http)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/prometheus")
                    .hasAuthority("SCOPE_agent:metrics")
                    .requestMatchers(HttpMethod.POST, "/api/runs/*/decision")
                    .hasAuthority("SCOPE_agent:approve")
                    .requestMatchers(HttpMethod.GET, "/api/runs/*")
                    .hasAnyAuthority("SCOPE_agent:use", "SCOPE_agent:approve")
                    .requestMatchers("/api/**")
                    .hasAuthority("SCOPE_agent:use")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  @Profile("!local & !test")
  JwtDecoder decoder(
      @Value("${security.issuer}") String issuer, @Value("${security.audience}") String audience) {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
    decoder.setJwtValidator(issuerAudienceValidator(issuer, audience));
    return decoder;
  }

  static OAuth2TokenValidator<Jwt> issuerAudienceValidator(String issuer, String audience) {
    OAuth2TokenValidator<Jwt> aud =
        token ->
            token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience", null));
    return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), aud);
  }

  @Bean
  @Profile({"local", "test"})
  SecurityFilterChain local(HttpSecurity http) throws Exception {
    return base(http)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/runs/*/decision")
                    .hasRole("APPROVER")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  @Profile({"local", "test"})
  UserDetailsService localUsers() {
    return new InMemoryUserDetailsManager(
        User.withUsername("developer").password("{noop}local-only").roles("USER").build(),
        User.withUsername("reviewer").password("{noop}local-only").roles("APPROVER").build());
  }
}
