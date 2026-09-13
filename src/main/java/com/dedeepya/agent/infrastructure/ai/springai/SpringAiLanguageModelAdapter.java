package com.dedeepya.agent.infrastructure.ai.springai;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;
import com.dedeepya.agent.domain.model.RunState;
import com.dedeepya.agent.domain.port.ModelPort;
import com.dedeepya.agent.domain.port.out.LanguageModelPort;
import com.dedeepya.agent.exception.ApiException;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agent.provider", havingValue = "SPRING_AI", matchIfMissing = true)
public class SpringAiLanguageModelAdapter implements LanguageModelPort {
  private final ModelPort model;
  private final AgentProperties properties;

  public SpringAiLanguageModelAdapter(ModelPort model, AgentProperties properties) {
    this.model = model;
    this.properties = properties;
  }

  @Override
  public AssistantReply complete(AssistantPrompt prompt) {
    var state = new RunState();
    state.model = properties.model();
    if (!prompt.systemInstruction().isBlank()) {
      state.transcript.add(
          Map.of("role", "system", "content", prompt.systemInstruction()));
    }
    for (var message : prompt.inputMessages()) {
      state.transcript.add(
          Map.of(
              "role", message.role().wireValue(),
              "content", message.content()));
    }

    var result =
        model.generate(
            state,
            false,
            Instant.now().plus(properties.providerTimeout()),
            ignored -> {});
    if (result.outcome() != ModelPort.Outcome.COMPLETED || result.text().isBlank()) {
      throw ApiException.bad(
          "PROVIDER_REQUEST_FAILED", "The model did not return a completed answer");
    }
    return new AssistantReply(result.text(), "spring-ai", properties.model());
  }
}
