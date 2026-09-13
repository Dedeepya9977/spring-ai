package com.dedeepya.agent.application.usecase;

import com.dedeepya.agent.security.Actor;
import java.util.UUID;

/** Cancels a run owned by the requesting actor. */
public interface RunCancellationUseCase {
  void cancel(UUID id, Actor actor);
}
