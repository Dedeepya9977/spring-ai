package com.dedeepya.agent.application.service.impl;

import com.dedeepya.agent.application.service.AgentService;
import com.dedeepya.agent.application.service.event.AgentEvent;
import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.domain.port.ModelPort;
import com.dedeepya.agent.dto.request.DecisionRequest;
import com.dedeepya.agent.dto.request.RunRequest;
import com.dedeepya.agent.dto.response.RunResponse;
import com.dedeepya.agent.engine.*;
import com.dedeepya.agent.exception.*;
import com.dedeepya.agent.infrastructure.persistence.RunStore;
import com.dedeepya.agent.infrastructure.persistence.RunStore.Run;
import com.dedeepya.agent.security.Actor;
import com.dedeepya.agent.tools.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AgentServiceImpl implements AgentService {
  private final RunStore store;
  private final ModelPort model;
  private final ToolExecutor tools;
  private final BudgetPolicy budgets;
  private final OutputPolicy output;
  private final MeterRegistry metrics;
  private final Semaphore slots;

  public AgentServiceImpl(
      RunStore store,
      ModelPort model,
      ToolExecutor tools,
      BudgetPolicy budgets,
      OutputPolicy output,
      MeterRegistry metrics,
      AgentProperties config) {
    this.store = store;
    this.model = model;
    this.tools = tools;
    this.budgets = budgets;
    this.output = output;
    this.metrics = metrics;
    this.slots = new Semaphore(config.concurrency());
  }

  @Override
  public RunResponse run(
      UUID session,
      Actor actor,
      String key,
      RunRequest request,
      Consumer<AgentEvent> events,
      AtomicBoolean cancelled) {
    acquire();
    try {
      var start = store.begin(session, actor, key, request);
      if (!start.created()) return store.view(start.run());
      execute(start.run(), events, cancelled);
      return store.view(store.load(start.run().id()));
    } finally {
      slots.release();
    }
  }

  @Override
  public RunResponse decide(UUID id, Actor actor, DecisionRequest decision) {
    acquire();
    try {
      Run run = store.decide(id, actor, decision);
      execute(run, e -> {}, new AtomicBoolean(false));
      return store.view(store.load(id));
    } finally {
      slots.release();
    }
  }

  @Override
  public RunResponse get(UUID id, Actor actor) {
    return store.view(store.visible(id, actor));
  }

  @Override
  public void cancel(UUID id, Actor actor) {
    store.cancel(id, actor);
  }

  private void acquire() {
    if (!slots.tryAcquire())
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "CAPACITY_LIMIT",
          "All run slots are busy; retry later with the same idempotency key");
  }

  private void execute(Run run, Consumer<AgentEvent> events, AtomicBoolean cancelled) {
    long started = System.nanoTime();
    try {
      events.accept(new AgentEvent("run", Map.of("id", run.id())));
      boolean agent = "AGENT".equals(run.mode());
      if (!agent && run.state().steps == 0) {
        if (run.state().orderId == null)
          throw ApiException.bad("ORDER_REQUIRED", "ORDER_STATUS requires orderId");
        // Code selects the lookup, independent of the model's tool choices.
        var reply = tools.lookupOrder(run.tenant(), run.state().orderId);
        if (reply.ok()) run.state().evidence.add((String) reply.data().get("evidenceId"));
        run.state()
            .transcript
            .add(
                Map.of(
                    "role",
                    "user",
                    "content",
                    "<untrusted_order_data>"
                        + escapeXml(Jsons.write(reply))
                        + "</untrusted_order_data>"));
      }
      while (true) {
        if (cancelled.get())
          throw ApiException.conflict("CLIENT_DISCONNECTED", "Client stopped receiving this run");
        // Deterministic pre-model hook: reserve budgets and fence cancelled workers.
        budgets.reserve(run.state(), run.deadline());
        store.checkpoint(run);
        ModelPort.Result result =
            model.generate(
                run.state(),
                agent,
                run.deadline(),
                delta -> {
                  if (cancelled.get())
                    throw ApiException.conflict("CLIENT_DISCONNECTED", "Client disconnected");
                  events.accept(
                      new AgentEvent("delta", Map.of("text", delta, "provisional", true)));
                });
        budgets.record(run.state(), result);
        metrics.counter("agent.tokens", "kind", "input").increment(result.inputTokens());
        metrics.counter("agent.tokens", "kind", "output").increment(result.outputTokens());
        store.checkpoint(run);
        BudgetPolicy.checkTime(run.deadline());
        if (cancelled.get())
          throw ApiException.conflict("CLIENT_DISCONNECTED", "Client disconnected");
        switch (result.outcome()) {
          case REFUSED -> {
            store.terminal(run.id(), "REFUSED", "MODEL_REFUSAL");
            return;
          }
          case INCOMPLETE ->
              throw ApiException.bad(
                  "MODEL_INCOMPLETE",
                  "Truncated output cannot be executed or returned as a valid answer");
          case FAILED ->
              throw ApiException.bad("PROVIDER_FAILED", "Provider did not complete the response");
          case COMPLETED -> {
            run.state().answer = output.validate(result.text(), run.state());
            run.state().transcript.addAll(result.outputItems());
            store.complete(run);
            return;
          }
          case TOOLS -> {
            if (!agent || result.calls().size() != 1)
              throw ApiException.bad("TOOL_PROTOCOL", "Expected one allowed tool call");
            run.state().transcript.addAll(result.outputItems());
            var call = result.calls().get(0);
            if (call.id() == null || call.id().length() > 200)
              throw ApiException.bad("TOOL_PROTOCOL", "Invalid tool call identifier");
            // No partial streamed arguments are ever executed.
            store.checkpoint(run);
            ToolExecutor.Reply reply;
            if ("propose_credit".equals(call.name())) {
              if (store.hasDecision(run.id()))
                reply =
                    ToolExecutor.Reply.error(
                        "PERMISSION_DENIED", false, "Only one human decision is allowed per run");
              else {
                reply = tools.validateProposal(run.tenant(), call);
                if (reply.ok()) {
                  run.state().evidence.add((String) reply.data().get("evidenceId"));
                  store.waitForApproval(run, call);
                  return;
                }
              }
            } else reply = tools.executeRead(run.tenant(), call);
            ToolExecutor.appendResult(run.state(), call.id(), reply);
            events.accept(
                new AgentEvent(
                    "tool", Map.of("name", safeToolName(call.name()), "ok", reply.ok())));
            metrics
                .counter(
                    "agent.tool.calls",
                    "tool",
                    safeToolName(call.name()),
                    "success",
                    String.valueOf(reply.ok()))
                .increment();
            store.checkpoint(run);
          }
        }
      }
    } catch (ApiException ex) {
      store.terminal(run.id(), "FAILED", ex.code());
    } catch (RuntimeException ex) {
      LoggerFactory.getLogger(getClass())
          .warn("agent_failed runId={} category={}", run.id(), ex.getClass().getSimpleName());
      store.terminal(run.id(), "FAILED", "INTEGRATION_FAILURE");
    } finally {
      metrics.timer("agent.run.duration").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }
  }

  private static String safeToolName(String name) {
    return ToolCatalog.definitions().stream().anyMatch(d -> d.name().equals(name))
        ? name
        : "unknown";
  }

  private static String escapeXml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  @Scheduled(fixedDelayString = "PT30S")
  public void recover() {
    store.recoverExpired();
  }

  @Scheduled(fixedDelayString = "PT1H")
  public void purge() {
    store.purgeConversations();
  }
}
