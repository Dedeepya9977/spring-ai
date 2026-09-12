package com.dedeepya.agent.domain.model;

import java.util.Objects;

public record AssistantPrompt(String message) {
  public AssistantPrompt {
    Objects.requireNonNull(message, "message must not be null");
    if (message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
  }
}
