package com.dedeepya.agent;

import static org.assertj.core.api.Assertions.*;

import com.dedeepya.agent.api.*;
import com.dedeepya.agent.api.Contracts.*;
import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.engine.*;
import com.dedeepya.agent.persistence.RunStore;
import com.dedeepya.agent.security.Actor;
import com.dedeepya.agent.tools.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RunLifecycleTest {
  @Autowired AgentService service;
  @Autowired RunStore store;
  @Autowired JdbcTemplate jdbc;
  @Autowired ToolExecutor tools;
  @Autowired BudgetPolicy budgets;
  @Autowired OutputPolicy output;
  @Autowired MeterRegistry metrics;
  @Autowired AgentProperties config;
  final Actor developer = new Actor("demo", "developer", false);
  final Actor reviewer = new Actor("demo", "reviewer", true);

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM service_credits");
    jdbc.update("DELETE FROM sessions");
    jdbc.update("DELETE FROM audit_events");
  }

  RunRequest request(boolean credit) {
    return new RunRequest(
        credit ? "Please request a credit for ORD-1001" : "Find ORD-1001", Mode.AGENT, null, null);
  }

  RunView run(UUID session, String key, RunRequest request) {
    return service.run(session, developer, key, request, e -> {}, new AtomicBoolean());
  }

  @Test
  void normalLookupCompletes() {
    var view = run(store.createSession(developer), "normal-001", request(false));
    assertThat(view.status()).isEqualTo("COMPLETED");
    assertThat(view.answer().evidence()).contains("order:ORD-1001");
  }

  @Test
  void durableApprovalCreatesNoCreditUntilDifferentHumanApproves() {
    var pending = run(store.createSession(developer), "credit-001", request(true));
    assertThat(pending.status()).isEqualTo("WAITING_APPROVAL");
    assertThat(countCredits()).isZero();
    assertThat(store.load(pending.id()).state().transcript).isNotEmpty();
    assertThat(store.view(store.load(pending.id())).pending().arguments()).contains("10000");
    var result =
        service.decide(
            pending.id(), reviewer, new Decision(true, "Reviewed delayed service evidence"));
    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer().recommendedAction()).isEqualTo(Answer.Action.CREDIT_RECORDED);
    assertThat(countCredits()).isEqualTo(1);
    assertThatThrownBy(
            () ->
                service.decide(
                    pending.id(), reviewer, new Decision(true, "Replay the same decision")))
        .isInstanceOf(ApiException.class);
    assertThat(countCredits()).isEqualTo(1);
  }

  @Test
  void humanDenialDoesNotCreateCredit() {
    var pending = run(store.createSession(developer), "deny-00001", request(true));
    var result =
        service.decide(
            pending.id(), reviewer, new Decision(false, "Insufficient service evidence"));
    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(countCredits()).isZero();
  }

  @Test
  void sameUserAndOtherTenantCannotApprove() {
    var pending = run(store.createSession(developer), "deny-00002", request(true));
    assertThatThrownBy(
            () ->
                service.decide(
                    pending.id(),
                    new Actor("demo", "developer", true),
                    new Decision(true, "I approve my own action")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("different person");
    assertThatThrownBy(
            () -> store.visible(pending.id(), new Actor("other-tenant", "reviewer", true)))
        .hasMessage("Resource not found");
    assertThat(countCredits()).isZero();
  }

  @Test
  void idempotencyReplaysAndRejectsChangedPayload() {
    UUID session = store.createSession(developer);
    var first = run(session, "same-00001", request(false));
    var replay = run(session, "same-00001", request(false));
    assertThat(replay.id()).isEqualTo(first.id());
    assertThatThrownBy(() -> run(session, "same-00001", request(true)))
        .hasMessageContaining("different request");
  }

  @Test
  void concurrentSameKeyCreatesOneRun() throws Exception {
    UUID session = store.createSession(developer);
    ExecutorService executor = Executors.newFixedThreadPool(6);
    try {
      List<Callable<RunStore.Started>> calls = new ArrayList<>();
      for (int i = 0; i < 6; i++)
        calls.add(() -> store.begin(session, developer, "concurrent-001", request(false)));
      var results =
          executor.invokeAll(calls).stream()
              .map(
                  f -> {
                    try {
                      return f.get();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .toList();
      assertThat(results.stream().filter(RunStore.Started::created).count()).isEqualTo(1);
      assertThat(results.stream().map(r -> r.run().id()).distinct().count()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void pendingRunBlocksAnotherRunAndExpiresWithoutEffect() {
    UUID session = store.createSession(developer);
    var pending = run(session, "expire-001", request(true));
    assertThatThrownBy(() -> run(session, "expire-002", request(false)))
        .hasMessageContaining("active run");
    jdbc.update(
        "UPDATE runs SET deadline=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
        pending.id());
    store.recoverExpired();
    assertThat(store.load(pending.id()).status()).isEqualTo("EXPIRED");
    assertThat(run(session, "expire-003", request(false)).status()).isEqualTo("COMPLETED");
    assertThat(countCredits()).isZero();
  }

  @Test
  void cancelledWorkerCannotOverwriteState() {
    UUID session = store.createSession(developer);
    var start = store.begin(session, developer, "cancel-001", request(false));
    store.cancel(start.run().id(), developer);
    assertThatThrownBy(() -> store.checkpoint(start.run()))
        .hasMessageContaining("no longer executable");
    assertThat(store.load(start.run().id()).status()).isEqualTo("CANCELLED");
  }

  @Test
  void staleOrderPolicyRecheckedAtApprovalTime() {
    var pending = run(store.createSession(developer), "stale-0001", request(true));
    try {
      jdbc.update(
          "UPDATE service_orders SET status='DELIVERED' WHERE tenant='demo' AND"
              + " order_id='ORD-1001'");
      assertThatThrownBy(
              () ->
                  service.decide(
                      pending.id(), reviewer, new Decision(true, "Review old delayed service")))
          .hasMessageContaining("no longer satisfies");
      assertThat(countCredits()).isZero();
    } finally {
      jdbc.update(
          "UPDATE service_orders SET status='DELAYED' WHERE tenant='demo' AND order_id='ORD-1001'");
    }
  }

  @Test
  void toolCannotReadAnotherTenant() {
    assertThat(tools.lookupOrder("demo", "ORD-9001").errorCategory()).isEqualTo("NOT_FOUND");
  }

  @Test
  void truncatedToolCallNeverExecutes() {
    ModelPort truncated =
        (s, t, d, c) ->
            new ModelPort.Result(
                ModelPort.Outcome.INCOMPLETE,
                List.of(),
                List.of(
                    new ModelPort.ToolCall("x", "propose_credit", "{\"orderId\":\"ORD-1001\"}")),
                "",
                10,
                0,
                20);
    var agent = new AgentService(store, truncated, tools, budgets, output, metrics, config);
    var result =
        agent.run(
            store.createSession(developer),
            developer,
            "truncate-1",
            request(true),
            e -> {},
            new AtomicBoolean());
    assertThat(result.errorCode()).isEqualTo("MODEL_INCOMPLETE");
    assertThat(countCredits()).isZero();
  }

  @Test
  void maliciousToolLoopStopsAtABound() {
    ModelPort malicious =
        (s, t, d, c) -> StubModel.call("execute_sql", "{\"sql\":\"delete everything\"}");
    var agent = new AgentService(store, malicious, tools, budgets, output, metrics, config);
    var result =
        agent.run(
            store.createSession(developer),
            developer,
            "attack-001",
            request(false),
            e -> {},
            new AtomicBoolean());
    assertThat(result.errorCode()).isIn("STEP_LIMIT", "BUDGET_LIMIT");
    assertThat(countCredits()).isZero();
  }

  long countCredits() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM service_credits", Long.class);
  }
}
