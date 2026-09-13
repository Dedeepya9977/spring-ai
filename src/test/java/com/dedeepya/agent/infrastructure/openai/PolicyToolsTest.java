package com.dedeepya.agent.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dedeepya.agent.domain.port.PolicyGateway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;

class PolicyToolsTest {
  @Test
  void exposesReadOnlyPolicyToolWithAnExplicitSchema() {
    PolicyGateway gateway =
        topic -> {
          if (!"service_credit".equals(topic))
            throw new IllegalArgumentException("Unsupported policy");
          return PolicyGateway.policy();
        };

    ToolCallback callback = ToolCallbacks.from(new PolicyTools(gateway))[0];

    assertThat(callback.getToolDefinition().name()).isEqualTo("search_policy");
    assertThat(callback.getToolDefinition().description()).contains("never changes data");
    assertThat(callback.getToolDefinition().inputSchema()).contains("topic");
    assertThat(callback.call("{\"topic\":\"service_credit\"}")).contains("service_credit");
    assertThatThrownBy(() -> callback.call("{\"topic\":\"unknown\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
