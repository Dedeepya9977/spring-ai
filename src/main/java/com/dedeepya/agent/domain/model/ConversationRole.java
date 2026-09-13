package com.dedeepya.agent.domain.model;

public enum ConversationRole {
  SYSTEM("system"),
  USER("user"),
  ASSISTANT("assistant");

  private final String wireValue;

  ConversationRole(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
