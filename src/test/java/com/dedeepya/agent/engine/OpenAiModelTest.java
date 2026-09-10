package com.dedeepya.agent.engine;

import static org.assertj.core.api.Assertions.*;

import com.dedeepya.agent.Fixtures;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.time.*;
import java.util.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;

class OpenAiModelTest {
  MockWebServer server;
  OpenAIClient client;
  OpenAiModel model;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        OpenAIOkHttpClient.builder()
            .baseUrl(server.url("/v1/").toString())
            .apiKey("test-key-not-real")
            .maxRetries(0)
            .build();
    model = new OpenAiModel(client, Fixtures.config());
  }

  @AfterEach
  void close() throws Exception {
    client.close();
    server.shutdown();
  }

  RunState state() {
    var s = new RunState();
    s.model = "gpt-4.1-mini";
    s.transcript.add(Map.of("role", "user", "content", "Find ORD-1001"));
    return s;
  }

  @Test
  void sdkStreamsStrictResponsesContract() throws Exception {
    String text =
        "{\"summary\":\"Please provide"
            + " details\",\"orderId\":null,\"recommendedAction\":\"ASK_DETAILS\",\"evidence\":[]}";
    server.enqueue(sse(delta("partial", 0) + terminal("completed", List.of(message(text)), 1)));
    var deltas = new ArrayList<String>();
    var result = model.generate(state(), true, Instant.now().plusSeconds(5), deltas::add);
    assertThat(result.outcome()).isEqualTo(ModelPort.Outcome.COMPLETED);
    assertThat(deltas).containsExactly("partial");
    assertThat(result.text()).isEqualTo(text);
    assertThat(result.cachedInputTokens()).isEqualTo(2);
    var request = server.takeRequest();
    var json = Jsons.tree(request.getBody().readUtf8());
    assertThat(request.getPath()).isEqualTo("/v1/responses");
    assertThat(json.get("store").asBoolean()).isFalse();
    assertThat(json.get("stream").asBoolean()).isTrue();
    assertThat(json.at("/text/format/strict").asBoolean()).isTrue();
    assertThat(json.get("parallel_tool_calls").asBoolean()).isFalse();
    assertThat(json.at("/tools/0/parameters/additionalProperties").asBoolean()).isFalse();
  }

  @Test
  void retries429BeforeFirstEvent() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Retry-After", "0")
            .setBody("{\"error\":{\"message\":\"busy\",\"type\":\"rate_limit_error\"}}"));
    server.enqueue(sse(terminal("completed", List.of(message("{}")), 0)));
    assertThat(model.generate(state(), false, Instant.now().plusSeconds(5), d -> {}).outcome())
        .isEqualTo(ModelPort.Outcome.COMPLETED);
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void doesNotRetryAuthenticationFailures() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(401)
            .setBody("{\"error\":{\"message\":\"bad key\",\"type\":\"invalid_request_error\"}}"));
    assertThatThrownBy(() -> model.generate(state(), false, Instant.now().plusSeconds(5), d -> {}))
        .hasMessage("Provider request failed");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void rejectsBrokenStreamWithoutReplaying() {
    server.enqueue(sse(delta("{\"summary\":", 0)));
    assertThatThrownBy(() -> model.generate(state(), true, Instant.now().plusSeconds(5), d -> {}))
        .hasMessageContaining("without a terminal event");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void incompleteStatusWinsOverToolCall() {
    var call =
        Map.<String, Object>of(
            "type",
            "function_call",
            "call_id",
            "call1",
            "name",
            "propose_credit",
            "arguments",
            "{}",
            "id",
            "fc_1",
            "status",
            "completed");
    server.enqueue(sse(terminal("incomplete", List.of(call), 0)));
    assertThat(model.generate(state(), true, Instant.now().plusSeconds(5), d -> {}).outcome())
        .isEqualTo(ModelPort.Outcome.INCOMPLETE);
  }

  @Test
  void refusalIsSeparateFromCompletedStatus() {
    var refusal =
        Map.<String, Object>of(
            "id",
            "msg1",
            "type",
            "message",
            "role",
            "assistant",
            "status",
            "completed",
            "content",
            List.of(Map.of("type", "refusal", "refusal", "Cannot comply")));
    server.enqueue(sse(terminal("completed", List.of(refusal), 0)));
    assertThat(model.generate(state(), true, Instant.now().plusSeconds(5), d -> {}).outcome())
        .isEqualTo(ModelPort.Outcome.REFUSED);
  }

  @Test
  void toolCallsAreMappedByCallId() {
    var call =
        Map.<String, Object>of(
            "type",
            "function_call",
            "call_id",
            "call_123",
            "name",
            "lookup_order",
            "arguments",
            "{\"orderId\":\"ORD-1001\"}",
            "id",
            "fc_1",
            "status",
            "completed");
    server.enqueue(sse(terminal("completed", List.of(call), 0)));
    var result = model.generate(state(), true, Instant.now().plusSeconds(5), d -> {});
    assertThat(result.outcome()).isEqualTo(ModelPort.Outcome.TOOLS);
    assertThat(result.calls().get(0).id()).isEqualTo("call_123");
  }

  @Test
  void multiTurnReasoningAndVisionItemsSurviveSerialization() {
    var s = state();
    s.transcript.add(
        Map.of(
            "type",
            "reasoning",
            "id",
            "rs_1",
            "summary",
            List.of(),
            "encrypted_content",
            "opaque"));
    s.transcript.add(
        Map.of(
            "role",
            "user",
            "content",
            List.of(
                Map.of("type", "input_text", "text", "Read this"),
                Map.of(
                    "type",
                    "input_image",
                    "image_url",
                    "data:image/png;base64,iVBORw0KGgo=",
                    "detail",
                    "low"))));
    // Params wraps a body; inspect its SDK body directly for transport fidelity.
    String body =
        com.openai.core.ObjectMappers.jsonMapper()
            .valueToTree(model.parameters(s, true)._body())
            .toString();
    assertThat(body).contains("encrypted_content", "input_image", "input_text", "opaque");
  }

  static MockResponse sse(String events) {
    return new MockResponse().addHeader("Content-Type", "text/event-stream").setBody(events);
  }

  static String delta(String text, int sequence) {
    return "event: response.output_text.delta\ndata: "
        + Jsons.write(
            Map.of(
                "type",
                "response.output_text.delta",
                "sequence_number",
                sequence,
                "item_id",
                "msg1",
                "output_index",
                0,
                "content_index",
                0,
                "delta",
                text))
        + "\n\n";
  }

  static Map<String, Object> message(String text) {
    return Map.of(
        "id",
        "msg1",
        "type",
        "message",
        "role",
        "assistant",
        "status",
        "completed",
        "content",
        List.of(Map.of("type", "output_text", "text", text, "annotations", List.of())));
  }

  static String terminal(String status, List<Map<String, Object>> output, int sequence) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", "resp1");
    response.put("object", "response");
    response.put("created_at", 1700000000);
    response.put("status", status);
    response.put("model", "gpt-4.1-mini");
    response.put("output", output);
    response.put("parallel_tool_calls", false);
    response.put("tools", List.of());
    response.put("tool_choice", "auto");
    response.put("temperature", 1);
    response.put("top_p", 1);
    response.put("metadata", Map.of());
    response.put("error", null);
    response.put(
        "incomplete_details",
        status.equals("incomplete") ? Map.of("reason", "max_output_tokens") : null);
    response.put(
        "usage",
        Map.of(
            "input_tokens",
            10,
            "output_tokens",
            5,
            "total_tokens",
            15,
            "input_tokens_details",
            Map.of("cached_tokens", 2),
            "output_tokens_details",
            Map.of("reasoning_tokens", 0)));
    String type = "response." + status;
    return "event: "
        + type
        + "\ndata: "
        + Jsons.write(Map.of("type", type, "sequence_number", sequence, "response", response))
        + "\n\n";
  }
}
