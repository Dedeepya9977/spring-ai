package com.dedeepya.agent.service;

import com.dedeepya.agent.dto.response.SessionResponse;
import com.dedeepya.agent.security.Actor;
import java.util.UUID;

/** Session lifecycle operations with ownership checks. */
public interface SessionService {
  SessionResponse create(Actor actor);

  void delete(UUID id, Actor actor);
}
