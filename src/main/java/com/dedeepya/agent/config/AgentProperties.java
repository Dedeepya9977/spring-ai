package com.dedeepya.agent.config;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("agent")
public record AgentProperties(
    @NotNull Provider provider,
    @NotBlank String model,
    @NotBlank String economyModel,
    @Min(1) @Max(12) int maxSteps,
    @Min(256) @Max(8192) int maxOutputTokens,
    @Min(4096) int maxContextBytes,
    @Min(4096) long maxRunTokens,
    @NotNull Duration runTimeout,
    @NotNull Duration providerTimeout,
    @NotNull Duration approvalTtl,
    @Min(1) @Max(64) int concurrency,
    @NotNull @DecimalMin("0.01") BigDecimal maxRunCostUsd,
    @NotNull @DecimalMin("0.0") BigDecimal inputUsdPerMillion,
    @NotNull @DecimalMin("0.0") BigDecimal cachedInputUsdPerMillion,
    @NotNull @DecimalMin("0.0") BigDecimal outputUsdPerMillion,
    @NotNull Duration retention,
    @NotNull Mcp mcp) {
  public enum Provider {
    OPENAI,
    STUB
  }

  public record Mcp(boolean enabled, String javaCommand, String serverJar) {}

  public AgentProperties {
    if (runTimeout != null
        && (runTimeout.isNegative()
            || runTimeout.isZero()
            || runTimeout.compareTo(Duration.ofMinutes(5)) > 0))
      throw new IllegalArgumentException("run-timeout must be between zero and five minutes");
    if (providerTimeout != null && (providerTimeout.isNegative() || providerTimeout.isZero()))
      throw new IllegalArgumentException("provider-timeout must be positive");
    if (approvalTtl != null && (approvalTtl.isNegative() || approvalTtl.isZero()))
      throw new IllegalArgumentException("approval-ttl must be positive");
  }
}
