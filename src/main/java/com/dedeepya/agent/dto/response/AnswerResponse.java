package com.dedeepya.agent.dto.response;

import java.util.List;

public record AnswerResponse(
    String summary, String orderId, Action recommendedAction, List<String> evidence) {
  public enum Action {
    ANSWER,
    ASK_DETAILS,
    ESCALATE,
    CREDIT_RECORDED
  }
}
