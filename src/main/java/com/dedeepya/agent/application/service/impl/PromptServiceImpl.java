package com.dedeepya.agent.application.service.impl;

import com.dedeepya.agent.application.service.PromptService;
import com.dedeepya.agent.config.PromptProperties;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.ConversationMessage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PromptServiceImpl implements PromptService {
  private final PromptProperties properties;

  public PromptServiceImpl(PromptProperties properties) {
    this.properties = properties;
  }

  @Override
  public AssistantPrompt build(String userMessage, List<ConversationMessage> history) {
    return new AssistantPrompt(userMessage, properties.systemInstruction(), history);
  }
}
