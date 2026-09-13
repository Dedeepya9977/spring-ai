package com.dedeepya.agent.dto.request;

import com.dedeepya.agent.dto.RunMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RunRequest(
    @NotBlank @Size(max = 6000) String message,
    @NotNull RunMode mode,
    @Pattern(regexp = "ORD-[0-9]{4,10}") String orderId,
    @Size(max = 350000) String imageDataUrl) {}
