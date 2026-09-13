package com.dedeepya.agent.dto.response;

public record TokenUsageResponse(
    long inputTokens,
    long cachedInputTokens,
    long outputTokens,
    long reservedTokens,
    String estimatedCostUsd) {}
