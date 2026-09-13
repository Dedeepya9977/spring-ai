package com.dedeepya.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.dedeepya.agent.engine.Jsons;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackagedRestartIT {
  @TempDir Path directory;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private String base;

  @Test
  void approvalSurvivesImmediateRestartAndProducesOneReceipt() throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    base = "http://127.0.0.1:" + port;
    Process process = start(port, "first.log");
    try {
      assertThat(request("POST", "/sessions", null, null, null).statusCode()).isEqualTo(401);
      var created = request("POST", "/sessions", null, "developer", null);
      assertThat(created.statusCode()).isEqualTo(201);
      String session = Jsons.tree(created.body()).path("id").asText();
      String path = "/sessions/" + session + "/runs";
      String body = "{\"message\":\"Please request a credit for ORD-1001\",\"mode\":\"AGENT\"}";
      var pending = request("POST", path, body, "developer", "restart-check-001");
      assertThat(pending.statusCode()).isEqualTo(200);
      var run = Jsons.tree(pending.body());
      assertThat(run.path("status").asText()).isEqualTo("WAITING_APPROVAL");
      String runPath = "/runs/" + run.path("id").asText();
      String decision = "{\"approve\":true,\"reason\":\"Reviewed the service credit policy\"}";
      assertThat(request("POST", runPath + "/decision", decision, "developer", null).statusCode())
          .isEqualTo(403);

      stop(process);
      process = start(port, "second.log");
      var restored = request("GET", runPath, null, "reviewer", null);
      assertThat(restored.statusCode()).isEqualTo(200);
      assertThat(Jsons.tree(restored.body()).path("status").asText()).isEqualTo("WAITING_APPROVAL");
      var approved = request("POST", runPath + "/decision", decision, "reviewer", null);
      assertThat(approved.statusCode()).isEqualTo(200);
      var completed = Jsons.tree(approved.body());
      assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
      assertThat(completed.path("credits").size()).isEqualTo(1);
      var replay = request("POST", path, body, "developer", "restart-check-001");
      assertThat(replay.statusCode()).isEqualTo(200);
      assertThat(Jsons.tree(replay.body()).path("id")).isEqualTo(run.path("id"));
      assertThat(Jsons.tree(replay.body()).path("credits").size()).isEqualTo(1);

      var next = request("POST", "/sessions", null, "developer", null);
      String streamPath =
          "/sessions/" + Jsons.tree(next.body()).path("id").asText() + "/runs/stream";
      var stream =
          request(
              "POST",
              streamPath,
              "{\"message\":\"Find ORD-1001\",\"mode\":\"AGENT\"}",
              "developer",
              "stream-check-001");
      assertThat(stream.statusCode()).isEqualTo(200);
      assertThat(stream.body()).contains("event:final", "COMPLETED");
    } finally {
      stop(process);
    }
  }

  private Process start(int port, String logName) throws Exception {
    Path log = directory.resolve(logName);
    var builder =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-jar",
            Path.of("target/agent-foundations-1.0.0.jar").toAbsolutePath().toString(),
            "--spring.profiles.active=local",
            "--agent.provider=STUB",
            "--server.port=" + port);
    builder.directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
    // Exercise the packaged local profile's database URL in a fresh working directory.
    builder.environment().remove("SPRING_DATASOURCE_URL");
    Process process = builder.start();
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
      while (System.nanoTime() < deadline && process.isAlive()) {
        if (Files.readString(log).contains("Started Application in")) {
          var health =
              http.send(
                  HttpRequest.newBuilder(URI.create(base + "/actuator/health"))
                      .timeout(Duration.ofSeconds(2))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          if (health.statusCode() == 200) return process;
        }
        Thread.sleep(100);
      }
      throw new AssertionError("Packaged application did not start: " + Files.readString(log));
    } catch (Exception | AssertionError ex) {
      stop(process);
      throw ex;
    }
  }

  private HttpResponse<String> request(
      String method, String path, String body, String user, String key) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(base + "/api" + path)).timeout(Duration.ofSeconds(15));
    if (user != null)
      builder.header(
          "Authorization",
          "Basic "
              + Base64.getEncoder()
                  .encodeToString((user + ":local-only").getBytes(StandardCharsets.UTF_8)));
    if (key != null) builder.header("Idempotency-Key", key);
    if (body != null) builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void stop(Process process) throws InterruptedException {
    if (process == null || !process.isAlive()) return;
    process.destroy();
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      throw new AssertionError("Packaged application did not shut down gracefully");
    }
  }
}
