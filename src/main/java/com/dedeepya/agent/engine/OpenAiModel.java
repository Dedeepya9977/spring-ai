package com.dedeepya.agent.engine;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.exception.ApiException;
import com.dedeepya.agent.tools.ToolCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.errors.*;
import com.openai.models.responses.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Official OpenAI Java SDK; application-owned state, tools and retries. */
public class OpenAiModel implements ModelPort {
  private final OpenAIClient client;
  private final AgentProperties config;

  public OpenAiModel(OpenAIClient client, AgentProperties config) {
    this.client = client;
    this.config = config;
  }

  @Override
  public Result generate(
      RunState state, boolean toolsEnabled, Instant deadline, Consumer<String> delta) {
    ResponseCreateParams params = parameters(state, toolsEnabled);
    for (int attempt = 0; attempt < 2; attempt++) {
      BudgetPolicy.checkTime(deadline);
      Duration remaining = Duration.between(Instant.now(), deadline);
      Duration timeout =
          remaining.compareTo(config.providerTimeout()) < 0 ? remaining : config.providerTimeout();
      boolean receivedEvent = false;
      try (StreamResponse<ResponseStreamEvent> stream =
          client
              .responses()
              .createStreaming(params, RequestOptions.builder().timeout(timeout).build())) {
        Response terminal = null;
        var iterator = stream.stream().iterator();
        while (iterator.hasNext()) {
          BudgetPolicy.checkTime(deadline);
          var event = iterator.next();
          receivedEvent = true;
          event.outputTextDelta().ifPresent(d -> delta.accept(d.delta()));
          if (event.completed().isPresent()) terminal = event.completed().get().response();
          else if (event.incomplete().isPresent()) terminal = event.incomplete().get().response();
          else if (event.failed().isPresent()) terminal = event.failed().get().response();
          else if (event.error().isPresent())
            throw ApiException.bad("STREAM_ERROR", "Provider stream reported an error");
          if (terminal != null) break;
        }
        if (terminal == null)
          throw ApiException.bad(
              "STREAM_INTERRUPTED", "Provider stream ended without a terminal event");
        return map(terminal);
      } catch (OpenAIServiceException ex) {
        if (receivedEvent || attempt == 1 || !retryable(ex.statusCode()))
          throw ApiException.bad("PROVIDER_HTTP_" + ex.statusCode(), "Provider request failed");
        waitBeforeRetry(retryDelay(ex), deadline);
      } catch (OpenAIIoException ex) {
        // Do not replay a stream after ANY event. Token usage may be unknown.
        if (receivedEvent || attempt == 1)
          throw ApiException.bad("PROVIDER_IO", "Provider connection failed");
        waitBeforeRetry(
            Duration.ofMillis(250 + ThreadLocalRandom.current().nextInt(250)), deadline);
      }
    }
    throw new IllegalStateException("Unreachable retry state");
  }

  ResponseCreateParams parameters(RunState state, boolean toolsEnabled) {
    var mapper = ObjectMappers.jsonMapper();
    List<ResponseInputItem> input = mapper.convertValue(state.transcript, new TypeReference<>() {});
    var schema =
        mapper.convertValue(OutputPolicy.schema(), ResponseFormatTextJsonSchemaConfig.Schema.class);
    var builder =
        ResponseCreateParams.builder()
            .model(state.model)
            .instructions(ContextPolicy.SYSTEM)
            .inputOfResponse(input)
            .store(false)
            .parallelToolCalls(false)
            .maxOutputTokens(config.maxOutputTokens())
            // Required to round-trip reasoning items with provider storage disabled.
            .addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT)
            .text(
                ResponseTextConfig.builder()
                    .format(
                        ResponseFormatTextJsonSchemaConfig.builder()
                            .name("support_answer")
                            .strict(true)
                            .schema(schema)
                            .build())
                    .build());
    if (toolsEnabled)
      for (var definition : ToolCatalog.definitions())
        builder.addTool(
            FunctionTool.builder()
                .name(definition.name())
                .description(definition.description())
                .strict(true)
                .parameters(mapper.convertValue(definition.schema(), FunctionTool.Parameters.class))
                .build());
    return builder.build();
  }

  Result map(Response response) {
    List<Map<String, Object>> items =
        ObjectMappers.jsonMapper().convertValue(response.output(), new TypeReference<>() {});
    List<ToolCall> calls = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    boolean refused = false;
    for (var item : response.output()) {
      if (item.functionCall().isPresent()) {
        var f = item.functionCall().get();
        calls.add(new ToolCall(f.callId(), f.name(), f.arguments()));
      }
      if (item.message().isPresent())
        for (var c : item.message().get().content()) {
          if (c.refusal().isPresent()) refused = true;
          c.outputText().ifPresent(t -> text.append(t.text()));
        }
    }
    var usage = response.usage();
    long input = usage.map(ResponseUsage::inputTokens).orElse(0L);
    long cached = usage.map(u -> u.inputTokensDetails().cachedTokens()).orElse(0L);
    long output = usage.map(ResponseUsage::outputTokens).orElse(0L);
    // Status and content type are separate signals; never alias Claude stop_reason strings.
    String status = response.status().map(Object::toString).orElse("unknown");
    Outcome outcome;
    if (!"completed".equals(status))
      outcome = "incomplete".equals(status) ? Outcome.INCOMPLETE : Outcome.FAILED;
    else if (refused) outcome = Outcome.REFUSED;
    else if (!calls.isEmpty()) outcome = Outcome.TOOLS;
    else outcome = Outcome.COMPLETED;
    return new Result(outcome, items, calls, text.toString(), input, cached, output);
  }

  static boolean retryable(int status) {
    return status == 408 || status == 429 || status >= 500;
  }

  private static Duration retryDelay(OpenAIServiceException ex) {
    var values = ex.headers().values("retry-after");
    if (!values.isEmpty()) {
      try {
        return Duration.ofMillis((long) (Double.parseDouble(values.get(0)) * 1000));
      } catch (NumberFormatException ignored) {
        try {
          return Duration.between(
              Instant.now(),
              ZonedDateTime.parse(values.get(0), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        } catch (RuntimeException ignoredDate) {
          /* Use bounded jitter below. */
        }
      }
    }
    return Duration.ofMillis(250 + ThreadLocalRandom.current().nextInt(250));
  }

  private static void waitBeforeRetry(Duration delay, Instant deadline) {
    // Honor Retry-After only when it fits; never retry earlier than requested.
    if (delay.isNegative()) delay = Duration.ZERO;
    if (delay.compareTo(Duration.ofSeconds(3)) > 0 || !Instant.now().plus(delay).isBefore(deadline))
      throw ApiException.bad("RETRY_DEFERRED", "Retry-After does not fit this run's retry budget");
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw ApiException.bad("CANCELLED", "Run interrupted");
    }
  }
}
