package com.dedeepya.agent.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskAssistantRequest(@NotBlank @Size(max = 6000) String message) {}
