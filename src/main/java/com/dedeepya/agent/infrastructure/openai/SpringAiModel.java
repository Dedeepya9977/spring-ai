package com.dedeepya.agent.infrastructure.openai;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.domain.model.RunState;
import com.dedeepya.agent.domain.port.ModelPort;
import com.dedeepya.agent.engine.BudgetPolicy;
import com.dedeepya.agent.engine.ContextPolicy;
import com.dedeepya.agent.engine.Jsons;
import com.dedeepya.agent.engine.OutputPolicy;
import com.dedeepya.agent.exception.ApiException;
import com.dedeepya.agent.tools.ToolCatalog;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

/** Spring AI ChatClient over the official OpenAI SDK; the application owns the tool loop. */
public final class SpringAiModel implements ModelPort {
  private final ChatClient chatClient;
  private final AgentProperties config;

  public SpringAiModel(ChatModel chatModel, AgentProperties config) {
    this.config = config;
    this.chatClient =
        ChatClient.builder(chatModel)
            // Spring AI 2 auto-registers a tool-execution advisor unless explicitly disabled.
            // Returning a proposal to AgentService is essential for durable human approval.
            .defaultAdvisors(
                a ->
                    a.param(
                        ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false))
            .build();
  }

  @Override
  public Result generate(
      RunState state, boolean toolsEnabled, Instant deadline, Consumer<String> delta) {
    BudgetPolicy.checkTime(deadline);
    Duration remaining = Duration.between(Instant.now(), deadline);
    Duration timeout =
        remaining.compareTo(config.providerTimeout()) < 0 ? remaining : config.providerTimeout();
    var options =
        OpenAiChatOptions.builder()
            .model(state.model)
            .maxCompletionTokens(config.maxOutputTokens())
            .store(false)
            .parallelToolCalls(false)
            .strict(true)
            .streamUsage(true)
            .timeout(timeout)
            .maxRetries(0)
            .responseFormat(
                ResponseFormat.builder()
                    .jsonSchema(Jsons.write(OutputPolicy.schema()))
                    .strict(true)
                    .build())
            .toolCallbacks(toolsEnabled ? toolDefinitions() : List.of())
            .build();
    var collected = new Collected();
    try {
      chatClient.prompt(new Prompt(messages(state), options)).stream()
          .chatResponse()
          .doOnNext(
              chunk -> {
                BudgetPolicy.checkTime(deadline);
                collected.accept(chunk, delta);
              })
          .then()
          .block(timeout);
    } catch (ApiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      // Do not replay a partial stream. The SDK is also configured with zero retries.
      if (Thread.currentThread().isInterrupted())
        throw ApiException.bad("CANCELLED", "Run interrupted");
      throw ApiException.bad("PROVIDER_STREAM", "Provider stream failed or exceeded its deadline");
    }
    return collected.result();
  }

  static List<ToolCallback> toolDefinitions() {
    return ToolCatalog.definitions().stream()
        .map(
            d ->
                (ToolCallback)
                    new ToolCallback() {
                      @Override
                      public ToolDefinition getToolDefinition() {
                        return ToolDefinition.builder()
                            .name(d.name())
                            .description(d.description())
                            .inputSchema(Jsons.write(d.schema()))
                            .build();
                      }

                      @Override
                      public String call(String input) {
                        throw new IllegalStateException(
                            "Tools must execute through AgentService approval controls");
                      }
                    })
        .toList();
  }

  @SuppressWarnings("unchecked")
  public static List<Message> messages(RunState state) {
    List<Message> messages = new ArrayList<>();
    Map<String, String> toolNames = new HashMap<>();
    messages.add(new SystemMessage(ContextPolicy.SYSTEM));
    for (var item : state.transcript) {
      String type = Objects.toString(item.get("type"), "message");
      if ("function_call".equals(type)) {
        String id = (String) item.get("call_id"), name = (String) item.get("name");
        toolNames.put(id, name);
        messages.add(
            AssistantMessage.builder()
                .content("")
                .toolCalls(
                    List.of(
                        new AssistantMessage.ToolCall(
                            id, "function", name, (String) item.get("arguments"))))
                .build());
      } else if ("function_call_output".equals(type)) {
        String id = (String) item.get("call_id");
        if (!toolNames.containsKey(id))
          throw ApiException.bad("TOOL_PROTOCOL", "Tool result has no matching call");
        messages.add(
            ToolResponseMessage.builder()
                .responses(
                    List.of(
                        new ToolResponseMessage.ToolResponse(
                            id, toolNames.get(id), (String) item.get("output"))))
                .build());
      } else if ("message".equals(type)) {
        String role = (String) item.get("role");
        StringBuilder text = new StringBuilder();
        List<Media> media = new ArrayList<>();
        Object content = item.get("content");
        if (content instanceof String s) text.append(s);
        else if (content instanceof List<?> parts) {
          for (Object part : parts) {
            var block = (Map<String, Object>) part;
            switch (Objects.toString(block.get("type"), "")) {
              case "input_text", "output_text", "text" -> text.append((String) block.get("text"));
              case "input_image" -> {
                String url = (String) block.get("image_url");
                if (url == null || !url.startsWith("data:image/") || !url.contains(";base64,"))
                  throw ApiException.bad("INVALID_IMAGE", "Expected a validated inline image");
                var mime = MimeTypeUtils.parseMimeType(url.substring(5, url.indexOf(';')));
                media.add(
                    new Media(
                        mime,
                        new ByteArrayResource(
                            Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)))));
              }
              default ->
                  throw ApiException.bad(
                      "CONTEXT_FORMAT", "Unsupported message content; create a new session");
            }
          }
        }
        if ("user".equals(role))
          messages.add(UserMessage.builder().text(text.toString()).media(media).build());
        else if ("assistant".equals(role)) messages.add(new AssistantMessage(text.toString()));
        else if ("system".equals(role)) messages.add(new SystemMessage(text.toString()));
        else throw ApiException.bad("CONTEXT_FORMAT", "Unexpected conversation role");
      } else {
        // Responses-only reasoning items cannot be safely translated to Chat Completions.
        throw ApiException.bad(
            "CONTEXT_FORMAT", "Create a new session when changing provider adapters");
      }
    }
    return messages;
  }

  private static final class Collected {
    private final StringBuilder text = new StringBuilder();
    private final Map<String, ToolCall> calls = new LinkedHashMap<>();
    private String finish = "";
    private boolean refused;
    private long input, cached, output;

    void accept(ChatResponse chunk, Consumer<String> delta) {
      var usage = chunk.getMetadata().getUsage();
      if (usage.getTotalTokens() > 0) {
        input = usage.getPromptTokens();
        output = usage.getCompletionTokens();
        cached = Optional.ofNullable(usage.getCacheReadInputTokens()).orElse(0L);
      }
      if (chunk.getResults().size() > 1)
        throw ApiException.bad("PROVIDER_PROTOCOL", "Expected one response choice");
      for (var generation : chunk.getResults()) {
        var message = generation.getOutput();
        String reason = generation.getMetadata().getFinishReason();
        if (reason != null && !reason.isBlank()) finish = reason.toLowerCase(Locale.ROOT);
        refused |= !Objects.toString(message.getMetadata().get("refusal"), "").isBlank();
        if (message.getText() != null && !message.getText().isEmpty()) {
          text.append(message.getText());
          if (text.length() > 400000)
            throw ApiException.bad("PROVIDER_PROTOCOL", "Response exceeds size limit");
          delta.accept(message.getText());
        }
        for (var call : message.getToolCalls())
          calls.put(call.id(), new ToolCall(call.id(), call.name(), call.arguments()));
      }
    }

    Result result() {
      if (finish.isBlank())
        throw ApiException.bad("STREAM_INTERRUPTED", "Stream ended without a finish reason");
      if (input + output == 0)
        throw ApiException.bad(
            "USAGE_MISSING", "Stream ended without token usage; reservation retained");
      Outcome outcome =
          switch (finish) {
            case "length" -> Outcome.INCOMPLETE;
            case "content_filter" -> Outcome.REFUSED;
            case "tool_calls" -> calls.isEmpty() ? Outcome.FAILED : Outcome.TOOLS;
            case "stop" -> calls.isEmpty() ? Outcome.COMPLETED : Outcome.FAILED;
            default -> Outcome.FAILED;
          };
      if (refused) outcome = Outcome.REFUSED;
      List<Map<String, Object>> items = new ArrayList<>();
      if (!text.isEmpty())
        items.add(
            Map.of(
                "type",
                "message",
                "role",
                "assistant",
                "content",
                List.of(
                    Map.of(
                        "type",
                        "output_text",
                        "text",
                        text.toString(),
                        "annotations",
                        List.of()))));
      for (var call : calls.values())
        items.add(
            Map.of(
                "type",
                "function_call",
                "call_id",
                call.id(),
                "name",
                call.name(),
                "arguments",
                call.arguments()));
      return new Result(
          outcome, items, List.copyOf(calls.values()), text.toString(), input, cached, output);
    }
  }
}
