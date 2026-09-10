package com.dedeepya.agent.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class Contracts {
  private Contracts() {}

  public enum Mode {
    AGENT,
    ORDER_STATUS
  }

  public record RunRequest(
      @NotBlank @Size(max = 6000) String message,
      @NotNull Mode mode,
      @Pattern(regexp = "ORD-[0-9]{4,10}") String orderId,
      @Size(max = 350000) String imageDataUrl) {}

  public record Decision(
      @NotNull Boolean approve, @NotBlank @Size(min = 10, max = 500) String reason) {}

  public record Answer(
      String summary, String orderId, Action recommendedAction, List<String> evidence) {
    public enum Action {
      ANSWER,
      ASK_DETAILS,
      ESCALATE,
      CREDIT_RECORDED
    }
  }

  public record Pending(String callId, String tool, String arguments, Instant expiresAt) {}

  public record Usage(
      long inputTokens,
      long cachedInputTokens,
      long outputTokens,
      long reservedTokens,
      String estimatedCostUsd) {}

  public record CreditReceipt(
      UUID id, String orderId, long amountPaise, String approvedBy, Instant createdAt) {}

  public record RunView(
      UUID id,
      UUID sessionId,
      String status,
      Answer answer,
      Pending pending,
      Usage usage,
      List<CreditReceipt> credits,
      String errorCode,
      Instant createdAt) {}

  public record Event(String type, Object data) {}
}
