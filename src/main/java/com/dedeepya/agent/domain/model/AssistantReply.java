package com.dedeepya.agent.domain.model;

import java.util.Objects;

public record AssistantReply(String text, String provider, String model) {
  public AssistantReply {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(provider, "provider must not be null");
    Objects.requireNonNull(model, "model must not be null");
  }
}
