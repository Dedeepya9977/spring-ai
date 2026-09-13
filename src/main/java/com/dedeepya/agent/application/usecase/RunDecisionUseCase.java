package com.dedeepya.agent.application.usecase;

import com.dedeepya.agent.dto.request.DecisionRequest;
import com.dedeepya.agent.dto.response.RunResponse;
import com.dedeepya.agent.security.Actor;
import java.util.UUID;

/** Applies an authorized human decision to a pending run. */
public interface RunDecisionUseCase {
  RunResponse decide(UUID id, Actor actor, DecisionRequest decision);
}
