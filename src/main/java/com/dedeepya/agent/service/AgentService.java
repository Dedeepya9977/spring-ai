package com.dedeepya.agent.service;

import com.dedeepya.agent.dto.request.DecisionRequest;
import com.dedeepya.agent.dto.request.RunRequest;
import com.dedeepya.agent.dto.response.RunResponse;
import com.dedeepya.agent.security.Actor;
import com.dedeepya.agent.service.event.AgentEvent;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Application use cases shared by HTTP and streaming entry points. */
public interface AgentService {
  RunResponse run(
      UUID session,
      Actor actor,
      String key,
      RunRequest request,
      Consumer<AgentEvent> events,
      AtomicBoolean cancelled);

  RunResponse get(UUID id, Actor actor);

  RunResponse decide(UUID id, Actor actor, DecisionRequest decision);

  void cancel(UUID id, Actor actor);
}
