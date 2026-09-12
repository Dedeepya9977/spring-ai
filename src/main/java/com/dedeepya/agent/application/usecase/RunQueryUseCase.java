package com.dedeepya.agent.application.usecase;

import com.dedeepya.agent.dto.response.RunResponse;
import com.dedeepya.agent.security.Actor;
import java.util.UUID;

/** Reads a run visible to the authenticated actor. */
public interface RunQueryUseCase {
  RunResponse get(UUID id, Actor actor);
}
