package com.dedeepya.agent;

import static org.assertj.core.api.Assertions.*;

import com.dedeepya.agent.api.Contracts.*;
import com.dedeepya.agent.engine.AgentService;
import com.dedeepya.agent.persistence.RunStore;
import com.dedeepya.agent.security.Actor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@EnabledIfSystemProperty(named = "postgresIT", matches = "true")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PostgresIT {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired RunStore store;
  @Autowired AgentService service;
  @Autowired JdbcTemplate jdbc;

  @Test
  void postgresMigrationAndConcurrentApprovalCommitOneCredit() throws Exception {
    Actor user = new Actor("demo", "requester", false),
        reviewer = new Actor("demo", "reviewer", true);
    UUID session = store.createSession(user);
    var pending =
        service.run(
            session,
            user,
            "postgres-001",
            new RunRequest("Credit for ORD-1001", Mode.AGENT, null, null),
            e -> {},
            new AtomicBoolean());
    assertThat(pending.status()).isEqualTo("WAITING_APPROVAL");
    var executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Boolean>> calls =
          List.of(() -> approve(pending.id(), reviewer), () -> approve(pending.id(), reviewer));
      var results = executor.invokeAll(calls);
      assertThat(
              results.stream()
                  .map(
                      f -> {
                        try {
                          return f.get();
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                      })
                  .filter(Boolean::booleanValue)
                  .count())
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM service_credits WHERE run_id=?", Long.class, pending.id()))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private boolean approve(UUID id, Actor actor) {
    try {
      store.decide(id, actor, new Decision(true, "Confirmed the service delay"));
      return true;
    } catch (com.dedeepya.agent.api.ApiException expected) {
      return false;
    }
  }
}
