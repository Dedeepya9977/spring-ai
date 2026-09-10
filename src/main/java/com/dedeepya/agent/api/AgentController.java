package com.dedeepya.agent.api;

import com.dedeepya.agent.api.Contracts.*;
import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.engine.AgentService;
import com.dedeepya.agent.persistence.RunStore;
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
  private final AgentService service;
  private final RunStore store;
  private final ExecutorService streamExecutor;
  private final AgentProperties config;

  public AgentController(
      AgentService service,
      RunStore store,
      ExecutorService streamExecutor,
      AgentProperties config) {
    this.service = service;
    this.store = store;
    this.streamExecutor = streamExecutor;
    this.config = config;
  }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, UUID> session(Authentication auth) {
    return Map.of("id", store.createSession(Actor.from(auth)));
  }

  @DeleteMapping("/sessions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id, Authentication auth) {
    store.deleteSession(id, Actor.from(auth));
  }

  @PostMapping("/sessions/{sessionId}/runs")
  RunView run(
      @PathVariable UUID sessionId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody RunRequest request,
      Authentication auth) {
    return service.run(
        sessionId, Actor.from(auth), key, request, event -> {}, new AtomicBoolean(false));
  }

  @PostMapping(
      value = "/sessions/{sessionId}/runs/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter stream(
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
              RunView result =
                  service.run(
                      sessionId, actor, key, request, event -> send(emitter, event), cancelled);
              send(emitter, new Event("final", result));
              emitter.complete();
            } catch (ApiException ex) {
              try {
                send(
                    emitter,
                    new Event("error", Map.of("code", ex.code(), "message", ex.getMessage())));
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
  RunView get(@PathVariable UUID id, Authentication auth) {
    return store.view(store.visible(id, Actor.from(auth)));
  }

  @PostMapping("/runs/{id}/decision")
  RunView decide(
      @PathVariable UUID id, @Valid @RequestBody Decision decision, Authentication auth) {
    return service.decide(id, Actor.from(auth), decision);
  }

  @PostMapping("/runs/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(@PathVariable UUID id, Authentication auth) {
    store.cancel(id, Actor.from(auth));
  }

  private static void send(SseEmitter emitter, Event event) {
    try {
      emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
    } catch (IOException ex) {
      throw ApiException.conflict("CLIENT_DISCONNECTED", "Client disconnected");
    }
  }
}
