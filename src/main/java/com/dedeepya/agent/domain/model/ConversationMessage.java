package com.dedeepya.agent.domain.model;

import java.util.Objects;

public record ConversationMessage(ConversationRole role, String content) {
  public ConversationMessage {
    Objects.requireNonNull(role, "role must not be null");
    Objects.requireNonNull(content, "content must not be null");
    if (content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
  }

  public static ConversationMessage system(String content) {
    return new ConversationMessage(ConversationRole.SYSTEM, content);
  }

  public static ConversationMessage user(String content) {
    return new ConversationMessage(ConversationRole.USER, content);
  }

  public static ConversationMessage assistant(String content) {
    return new ConversationMessage(ConversationRole.ASSISTANT, content);
  }
}
