package com.dedeepya.agent.infrastructure.ai.stub;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;
import com.dedeepya.agent.domain.port.out.LanguageModelPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agent.provider", havingValue = "STUB")
public class StubLanguageModelAdapter implements LanguageModelPort {
  private final AgentProperties properties;

  public StubLanguageModelAdapter(AgentProperties properties) {
    this.properties = properties;
  }

  @Override
  public AssistantReply complete(AssistantPrompt prompt) {
    return new AssistantReply(
        "Local teaching response for: " + prompt.message(), "stub", properties.model());
  }
}
