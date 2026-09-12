package com.dedeepya.agent.dto.response;

import java.time.Instant;

public record PendingApprovalResponse(
    String callId, String tool, String arguments, Instant expiresAt) {}
