package com.dedeepya.agent.controller;

import com.dedeepya.agent.application.service.event.AgentEvent;
import com.dedeepya.agent.application.usecase.RunAgentUseCase;
import com.dedeepya.agent.application.usecase.RunCancellationUseCase;
import com.dedeepya.agent.application.usecase.RunDecisionUseCase;
import com.dedeepya.agent.application.usecase.RunQueryUseCase;
import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.dto.request.DecisionRequest;
import com.dedeepya.agent.dto.request.RunRequest;
import com.dedeepya.agent.dto.response.RunResponse;
import com.dedeepya.agent.exception.ApiException;
import com.dedeepya.agent.security.Actor;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class AgentController {
  private final RunAgentUseCase runAgent;
  private final RunQueryUseCase runQuery;
  private final RunDecisionUseCase runDecision;
  private final RunCancellationUseCase runCancellation;
  private final ExecutorService streamExecutor;
  private final AgentProperties config;

  public AgentController(
      RunAgentUseCase runAgent,
      RunQueryUseCase runQuery,
      RunDecisionUseCase runDecision,
      RunCancellationUseCase runCancellation,
      ExecutorService streamExecutor,
      AgentProperties config) {
    this.runAgent = runAgent;
    this.runQuery = runQuery;
    this.runDecision = runDecision;
    this.runCancellation = runCancellation;
    this.streamExecutor = streamExecutor;
    this.config = config;
  }

  @PostMapping("/sessions/{sessionId}/runs")
  public RunResponse run(
      @PathVariable UUID sessionId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody RunRequest request,
      Authentication auth) {
    return runAgent.run(
        sessionId, Actor.from(auth), key, request, event -> {}, new AtomicBoolean(false));
  }

  @PostMapping(
      value = "/sessions/{sessionId}/runs/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @PathVariable UUID sessionId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody RunRequest request,
      Authentication auth) {
    Actor actor = Actor.from(auth); // Capture identity before moving off the servlet thread.
    SseEmitter emitter = new SseEmitter(config.runTimeout().plusSeconds(5).toMillis());
    AtomicBoolean cancelled = new AtomicBoolean(false);
    emitter.onTimeout(() -> cancelled.set(true));
    emitter.onError(ex -> cancelled.set(true));
    emitter.onCompletion(() -> cancelled.set(true));
    try {
      streamExecutor.execute(
          () -> {
            try {
              RunResponse result =
                  runAgent.run(
                      sessionId, actor, key, request, event -> send(emitter, event), cancelled);
              send(emitter, new AgentEvent("final", result));
              emitter.complete();
            } catch (ApiException ex) {
              try {
                send(
                    emitter,
                    new AgentEvent("error", Map.of("code", ex.code(), "message", ex.getMessage())));
              } finally {
                emitter.complete();
              }
            } catch (RuntimeException ex) {
              emitter.completeWithError(new IllegalStateException("Stream stopped"));
            }
          });
    } catch (RejectedExecutionException ex) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "CAPACITY_LIMIT", "No stream worker is available");
    }
    return emitter;
  }

  @GetMapping("/runs/{id}")
  public RunResponse get(@PathVariable UUID id, Authentication auth) {
    return runQuery.get(id, Actor.from(auth));
  }

  @PostMapping("/runs/{id}/decision")
  public RunResponse decide(
      @PathVariable UUID id, @Valid @RequestBody DecisionRequest decision, Authentication auth) {
    return runDecision.decide(id, Actor.from(auth), decision);
  }

  @PostMapping("/runs/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable UUID id, Authentication auth) {
    runCancellation.cancel(id, Actor.from(auth));
  }

  private static void send(SseEmitter emitter, AgentEvent event) {
    try {
      emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
    } catch (IOException ex) {
      throw ApiException.conflict("CLIENT_DISCONNECTED", "Client disconnected");
    }
  }
}
