package com.dedeepya.agent.controller;

import com.dedeepya.agent.dto.response.SessionResponse;
import com.dedeepya.agent.security.Actor;
import com.dedeepya.agent.service.SessionService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
  private final SessionService service;

  public SessionController(SessionService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SessionResponse create(Authentication auth) {
    return service.create(Actor.from(auth));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, Authentication auth) {
    service.delete(id, Actor.from(auth));
  }
}
