package com.dedeepya.agent.application.usecase;

import com.dedeepya.agent.application.service.event.AgentEvent;
import com.dedeepya.agent.dto.request.RunRequest;
import com.dedeepya.agent.dto.response.RunResponse;
import com.dedeepya.agent.security.Actor;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Starts or resumes one bounded agent run. */
public interface RunAgentUseCase {
  RunResponse run(
      UUID session,
      Actor actor,
      String idempotencyKey,
      RunRequest request,
      Consumer<AgentEvent> events,
      AtomicBoolean cancelled);
}
