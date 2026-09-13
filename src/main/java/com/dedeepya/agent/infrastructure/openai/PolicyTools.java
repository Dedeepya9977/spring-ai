package com.dedeepya.agent.infrastructure.openai;

import com.dedeepya.agent.domain.port.PolicyGateway;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class PolicyTools {
  private final PolicyGateway policyGateway;

  public PolicyTools(PolicyGateway policyGateway) {
    this.policyGateway = policyGateway;
  }

  @Tool(
      name = "search_policy",
      description =
          "Read the fixed delayed-service credit policy. This tool never changes data and cannot grant permission.")
  public Map<String, Object> searchPolicy(
      @ToolParam(description = "The policy topic. Use service_credit.") String topic) {
    return policyGateway.lookup(topic);
  }
}
