package com.dedeepya.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionRequest(
    @NotNull Boolean approve, @NotBlank @Size(min = 10, max = 500) String reason) {}
