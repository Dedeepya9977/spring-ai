package com.dedeepya.agent;

import static org.assertj.core.api.Assertions.*;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.infrastructure.mcp.McpPolicyGateway;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class McpProcessIT {
  @Test
  void startsRealStdioServerNegotiatesAndReadsToolAndResource() {
    var config =
        new AgentProperties.Mcp(
            true,
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "target/agent-foundations-1.0.0.jar");
    try (var gateway = new McpPolicyGateway(config)) {
      assertThat(gateway.lookup("service_credit"))
          .containsEntry("evidenceId", "policy:service_credit:v1");
      assertThat(gateway.readPolicyResource().contents()).hasSize(1);
    }
  }
}
