package com.dedeepya.agent.application.service;

import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.ConversationMessage;
import java.util.List;

public interface PromptService {
  AssistantPrompt build(String userMessage, List<ConversationMessage> history);

  default AssistantPrompt build(String userMessage) {
    return build(userMessage, List.of());
  }
}
