package com.dedeepya.agent.engine;

import static org.assertj.core.api.Assertions.*;

import com.dedeepya.agent.Fixtures;
import com.dedeepya.agent.api.ApiException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.time.Instant;
import java.util.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

class SpringAiModelTest {
  MockWebServer server;
  OpenAIClient client;
  SpringAiModel model;

  @BeforeEach
  void setup() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        OpenAIOkHttpClient.builder()
            .baseUrl(server.url("/v1/").toString())
            .apiKey("test-key-not-real")
            .maxRetries(0)
            .build();
    model =
        new SpringAiModel(
            OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(client.async())
                .options(OpenAiChatOptions.builder().model("gpt-4.1-mini").maxRetries(0).build())
                .build(),
            Fixtures.config());
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
  void chatClientStreamsUsingOfficialSdkAndStrictContract() throws Exception {
    enqueue(
        chunk(Map.of("role", "assistant", "content", "hello"), null)
            + chunk(Map.of(), "stop")
            + usage());
    var deltas = new ArrayList<String>();
    var result = model.generate(state(), true, Instant.now().plusSeconds(5), deltas::add);
    assertThat(result.outcome()).isEqualTo(ModelPort.Outcome.COMPLETED);
    assertThat(result.text()).isEqualTo("hello");
    assertThat(deltas).containsExactly("hello");
    assertThat(result.inputTokens()).isEqualTo(10);
    assertThat(result.cachedInputTokens()).isEqualTo(2);
    var request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
    var body = Jsons.tree(request.getBody().readUtf8());
    assertThat(body.at("/response_format/json_schema/strict").asBoolean()).isTrue();
    assertThat(body.at("/tools/0/function/strict").asBoolean()).isTrue();
    assertThat(body.get("store").asBoolean()).isFalse();
    assertThat(body.get("parallel_tool_calls").asBoolean()).isFalse();
    assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(1200);
    assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
  }

  @Test
  void partialToolArgumentsAreAssembledAndReturnedWithoutExecutingCallbacks() {
    enqueue(
        chunk(
                Map.of(
                    "role",
                    "assistant",
                    "tool_calls",
                    List.of(
                        Map.of(
                            "index",
                            0,
                            "id",
                            "call-1",
                            "type",
                            "function",
                            "function",
                            Map.of("name", "propose_credit", "arguments", "{\"orderId\":")))),
                null)
            + chunk(
                Map.of(
                    "tool_calls",
                    List.of(
                        Map.of(
                            "index",
                            0,
                            "function",
                            Map.of(
                                "arguments",
                                "\"ORD-1001\",\"amountPaise\":100,\"reason\":\"Delayed"
                                    + " service\"}")))),
                "tool_calls")
            + usage());
    var result = model.generate(state(), true, Instant.now().plusSeconds(5), s -> {});
    assertThat(result.outcome()).isEqualTo(ModelPort.Outcome.TOOLS);
    assertThat(result.calls()).hasSize(1);
    assertThat(result.calls().get(0).name()).isEqualTo("propose_credit");
    assertThat(Jsons.tree(result.calls().get(0).arguments()).get("amountPaise").asInt())
        .isEqualTo(100);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({"length,INCOMPLETE", "content_filter,REFUSED", "stop,COMPLETED"})
  void finishReasonsAreMapped(String reason, ModelPort.Outcome expected) {
    enqueue(chunk(Map.of("content", "{}"), reason) + usage());
    assertThat(model.generate(state(), false, Instant.now().plusSeconds(5), s -> {}).outcome())
        .isEqualTo(expected);
  }

  @Test
  void refusalMetadataOverridesStop() {
    enqueue(chunk(Map.of("refusal", "Cannot assist"), "stop") + usage());
    assertThat(model.generate(state(), false, Instant.now().plusSeconds(5), s -> {}).outcome())
        .isEqualTo(ModelPort.Outcome.REFUSED);
  }

  @Test
  void missingFinishReasonIsRejectedWithoutRetry() {
    enqueue(chunk(Map.of("content", "partial"), null) + usage());
    assertThatThrownBy(() -> model.generate(state(), true, Instant.now().plusSeconds(5), s -> {}))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("finish reason");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void missingUsageDoesNotReleaseBudgetReservation() {
    enqueue(chunk(Map.of("content", "{}"), "stop"));
    assertThatThrownBy(() -> model.generate(state(), false, Instant.now().plusSeconds(5), s -> {}))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("usage");
  }

  @Test
  void transcriptPreservesToolCallAndResultPair() {
    var s = state();
    s.transcript.add(
        Map.of(
            "type",
            "function_call",
            "call_id",
            "c1",
            "name",
            "lookup_order",
            "arguments",
            "{\"orderId\":\"ORD-1001\"}"));
    s.transcript.add(
        Map.of(
            "type", "function_call_output", "call_id", "c1", "output", "{\"status\":\"DELAYED\"}"));
    var messages = SpringAiModel.messages(s);
    assertThat(messages.get(3)).isInstanceOf(ToolResponseMessage.class);
    assertThat(((ToolResponseMessage) messages.get(3)).getResponses().get(0).name())
        .isEqualTo("lookup_order");
  }

  @Test
  void unsupportedResponsesReasoningRequiresNewSession() {
    var s = state();
    s.transcript.add(Map.of("type", "reasoning", "encrypted_content", "opaque"));
    assertThatThrownBy(() -> SpringAiModel.messages(s))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("new session");
  }

  @Test
  void inlineImageIsMappedToSpringAiMedia() {
    var s = state();
    s.transcript.clear();
    s.transcript.add(
        Map.of(
            "role",
            "user",
            "content",
            List.of(
                Map.of("type", "input_text", "text", "Describe"),
                Map.of("type", "input_image", "image_url", "data:image/png;base64,iVBORw0KGgo="))));
    var message = (UserMessage) SpringAiModel.messages(s).get(1);
    assertThat(message.getText()).isEqualTo("Describe");
    assertThat(message.getMedia()).hasSize(1);
  }

  void enqueue(String body) {
    server.enqueue(
        new MockResponse()
            .addHeader("Content-Type", "text/event-stream")
            .setBody(body + "data: [DONE]\n\n"));
  }

  String chunk(Map<String, Object> delta, String finish) {
    Map<String, Object> choice = new LinkedHashMap<>();
    choice.put("index", 0);
    choice.put("delta", delta);
    choice.put("finish_reason", finish);
    return event(
        Map.of(
            "id",
            "chatcmpl-test",
            "object",
            "chat.completion.chunk",
            "created",
            1,
            "model",
            "gpt-4.1-mini",
            "choices",
            List.of(choice)));
  }

  String usage() {
    return event(
        Map.of(
            "id",
            "chatcmpl-test",
            "object",
            "chat.completion.chunk",
            "created",
            1,
            "model",
            "gpt-4.1-mini",
            "choices",
            List.of(),
            "usage",
            Map.of(
                "prompt_tokens",
                10,
                "completion_tokens",
                3,
                "total_tokens",
                13,
                "prompt_tokens_details",
                Map.of("cached_tokens", 2))));
  }

  String event(Map<String, Object> data) {
    return "data: " + Jsons.write(data) + "\n\n";
  }
}
