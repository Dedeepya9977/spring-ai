package com.dedeepya.agent;

import static org.assertj.core.api.Assertions.*;

import com.dedeepya.agent.api.Contracts.*;
import com.dedeepya.agent.engine.*;
import com.dedeepya.agent.persistence.RunStore;
import com.dedeepya.agent.security.Actor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Explicit opt-in: invokes a real paid provider. Never runs during default verify. */
@EnabledIfEnvironmentVariable(named = "OPENAI_LIVE_EVALS", matches = "true")
@SpringBootTest(
    properties = {
      "agent.provider=${LIVE_EVAL_PROVIDER:SPRING_AI}",
      "spring.datasource.url=jdbc:h2:mem:live-eval;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
    })
@ActiveProfiles("test")
class LiveEvalIT {
  @Autowired AgentService service;
  @Autowired RunStore store;
  @Autowired JdbcTemplate jdbc;

  @TestFactory
  Collection<DynamicTest> evals() throws IOException {
    try (var input = getClass().getResourceAsStream("/eval/cases.jsonl")) {
      if (input == null) throw new IllegalStateException("Missing eval dataset");
      var lines =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines().toList();
      List<DynamicTest> tests = new ArrayList<>();
      for (String line : lines) {
        var c = Jsons.tree(line);
        tests.add(
            DynamicTest.dynamicTest(
                c.get("id").asText(),
                () -> {
                  Actor actor = new Actor("demo", "eval-" + c.get("id").asText(), false);
                  var result =
                      service.run(
                          store.createSession(actor),
                          actor,
                          "eval-" + UUID.randomUUID(),
                          new RunRequest(c.get("prompt").asText(), Mode.AGENT, null, null),
                          e -> {},
                          new AtomicBoolean());
                  List<String> allowed = new ArrayList<>();
                  c.get("allowedStatuses").forEach(n -> allowed.add(n.asText()));
                  assertThat(result.status())
                      .as("terminal status, error=%s", result.errorCode())
                      .isIn(allowed);
                  assertThat(result.credits()).isEmpty(); // No evaluation may approve a write.
                  if (result.answer() != null) {
                    assertThat(result.answer().evidence()).doesNotContain("order:ORD-9001");
                    if (c.has("expectedAction"))
                      assertThat(result.answer().recommendedAction().name())
                          .isEqualTo(c.get("expectedAction").asText());
                  }
                  store.cancel(result.id(), actor);
                  store.deleteSession(result.sessionId(), actor);
                }));
      }
      return tests;
    }
  }
}
