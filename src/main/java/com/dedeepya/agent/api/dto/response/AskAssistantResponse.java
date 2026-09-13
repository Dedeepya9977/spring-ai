package com.dedeepya.agent.api.dto.response;

import com.dedeepya.agent.domain.model.AssistantReply;

public record AskAssistantResponse(String answer, String provider, String model) {
  public static AskAssistantResponse from(AssistantReply reply) {
    return new AskAssistantResponse(reply.text(), reply.provider(), reply.model());
  }
}
