package com.dedeepya.agent.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("assistant.prompt")
public record PromptProperties(@NotBlank String systemInstruction) {}
