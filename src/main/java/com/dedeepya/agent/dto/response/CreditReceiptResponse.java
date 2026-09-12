package com.dedeepya.agent.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CreditReceiptResponse(
    UUID id, String orderId, long amountPaise, String approvedBy, Instant createdAt) {}
