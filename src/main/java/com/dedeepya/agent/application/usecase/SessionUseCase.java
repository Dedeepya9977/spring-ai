package com.dedeepya.agent.application.usecase;

import com.dedeepya.agent.dto.response.SessionResponse;
import com.dedeepya.agent.security.Actor;
import java.util.UUID;

/** Creates and deletes sessions for an authenticated actor. */
public interface SessionUseCase {
  SessionResponse create(Actor actor);

  void delete(UUID id, Actor actor);
}
