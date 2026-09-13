package com.dedeepya.agent.domain.model;

import java.util.List;

/** Provider-independent answer accepted by the application's output policy. */
public record Answer(
    String summary, String orderId, Action recommendedAction, List<String> evidence) {
  public enum Action {
    ANSWER,
    ASK_DETAILS,
    ESCALATE,
    CREDIT_RECORDED
  }
}
