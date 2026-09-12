package com.dedeepya.agent.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunResponse(
    UUID id,
    UUID sessionId,
    String status,
    AnswerResponse answer,
    PendingApprovalResponse pending,
    TokenUsageResponse usage,
    List<CreditReceiptResponse> credits,
    String errorCode,
    Instant createdAt) {}
