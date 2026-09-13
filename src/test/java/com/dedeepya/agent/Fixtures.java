package com.dedeepya.agent;

import com.dedeepya.agent.config.AgentProperties;
import java.math.BigDecimal;
import java.time.Duration;

public final class Fixtures {
  private Fixtures() {}

  public static AgentProperties config() {
    return new AgentProperties(
        AgentProperties.Provider.STUB,
        "gpt-4.1-mini",
        "gpt-4.1-mini",
        6,
        1200,
        40000,
        200000,
        Duration.ofSeconds(20),
        Duration.ofSeconds(2),
        Duration.ofMinutes(15),
        4,
        new BigDecimal("0.25"),
        new BigDecimal("0.4"),
        new BigDecimal("0.1"),
        new BigDecimal("1.6"),
        Duration.ofDays(7),
        new AgentProperties.Mcp(false, "java", "target/agent-foundations-1.0.0.jar"));
  }
}
