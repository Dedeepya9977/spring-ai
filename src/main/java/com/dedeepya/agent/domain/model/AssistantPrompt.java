package com.dedeepya.agent.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AssistantPrompt(
    String message, String systemInstruction, List<ConversationMessage> history) {
  public AssistantPrompt {
    Objects.requireNonNull(message, "message must not be null");
    if (message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    systemInstruction = systemInstruction == null ? "" : systemInstruction;
    history = history == null ? List.of() : List.copyOf(history);
  }

  public AssistantPrompt(String message) {
    this(message, "", List.of());
  }

  public List<ConversationMessage> inputMessages() {
    var messages = new ArrayList<>(history);
    messages.add(ConversationMessage.user(message));
    return List.copyOf(messages);
  }
}
