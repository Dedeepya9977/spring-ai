package com.dedeepya.agent.infrastructure.ai.openai;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;
import com.dedeepya.agent.domain.port.out.LanguageModelPort;
import com.dedeepya.agent.exception.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agent.provider", havingValue = "OPENAI")
public class OpenAiLanguageModelAdapter implements LanguageModelPort {
  private final OpenAIClient client;
  private final AgentProperties properties;

  public OpenAiLanguageModelAdapter(OpenAIClient client, AgentProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public AssistantReply complete(AssistantPrompt prompt) {
    try {
      var parameters =
          ResponseCreateParams.builder()
              .model(properties.model())
              .instructions(prompt.systemInstruction())
              .inputOfResponse(toSdkMessages(prompt))
              .store(false)
              .maxOutputTokens(properties.maxOutputTokens())
              .build();

      Response response = client.responses().create(parameters);
      String answer =
          response.output().stream()
              .flatMap(item -> item.message().stream())
              .flatMap(message -> message.content().stream())
              .flatMap(content -> content.outputText().stream())
              .map(outputText -> outputText.text())
              .collect(Collectors.joining());

      if (answer.isBlank()) {
        throw ApiException.bad("EMPTY_MODEL_RESPONSE", "The model returned no text output");
      }

      return new AssistantReply(answer, "openai", properties.model());
    } catch (ApiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw ApiException.bad("PROVIDER_REQUEST_FAILED", "The model request could not be completed");
    }
  }

  private static List<ResponseInputItem> toSdkMessages(AssistantPrompt prompt) {
    var rawMessages =
        prompt.inputMessages().stream()
            .map(
                message ->
                    Map.of(
                        "role", message.role().wireValue(),
                        "content", message.content()))
            .toList();

    return ObjectMappers.jsonMapper()
        .convertValue(rawMessages, new TypeReference<List<ResponseInputItem>>() {});
  }
}
