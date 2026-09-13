package com.dedeepya.agent.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dedeepya.agent.domain.port.PolicyGateway;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

class PolicyToolsTest {
  @Test
  void exposesReadOnlyPolicyToolWithAnExplicitContract() throws Exception {
    PolicyGateway gateway =
        topic -> {
          if (!"service_credit".equals(topic))
            throw new IllegalArgumentException("Unsupported policy");
          return PolicyGateway.policy();
        };
    PolicyTools tools = new PolicyTools(gateway);
    Method method = PolicyTools.class.getDeclaredMethod("searchPolicy", String.class);
    Tool annotation = method.getAnnotation(Tool.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.name()).isEqualTo("search_policy");
    assertThat(annotation.description()).contains("never changes data");
    assertThat(tools.searchPolicy("service_credit")).containsEntry("topic", "service_credit");
    assertThatThrownBy(() -> tools.searchPolicy("unknown"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
