package com.dedeepya.agent.config;

import com.dedeepya.agent.engine.*;
import com.dedeepya.agent.mcp.McpPolicyGateway;
import com.dedeepya.agent.tools.PolicyGateway;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.micrometer.observation.ObservationRegistry;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class RuntimeConfiguration {
  @Bean
  TransactionTemplate transactions(PlatformTransactionManager manager) {
    TransactionTemplate template = new TransactionTemplate(manager);
    template.setTimeout(8);
    return template;
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnExpression("'${agent.provider:SPRING_AI}' != 'STUB'")
  OpenAIClient openAIClient(AgentProperties config) {
    String key = System.getenv("OPENAI_API_KEY");
    if (key == null || key.isBlank())
      throw new IllegalStateException(
          "Set OPENAI_API_KEY or select the explicit local STUB profile");
    return OpenAIOkHttpClient.builder()
        .apiKey(key)
        .maxRetries(0)
        .timeout(config.providerTimeout())
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "agent.provider", havingValue = "OPENAI")
  ModelPort realModel(OpenAIClient client, AgentProperties config) {
    return new OpenAiModel(client, config);
  }

  @Bean
  @ConditionalOnProperty(name = "agent.provider", havingValue = "SPRING_AI", matchIfMissing = true)
  ModelPort springAiModel(
      OpenAIClient client, AgentProperties config, ObservationRegistry observations) {
    var model =
        OpenAiChatModel.builder()
            .openAiClient(client)
            .openAiClientAsync(client.async())
            .options(
                OpenAiChatOptions.builder()
                    .model(config.model())
                    .timeout(config.providerTimeout())
                    .maxRetries(0)
                    .store(false)
                    .build())
            .observationRegistry(observations)
            .build();
    return new SpringAiModel(model, config);
  }

  @Bean
  @ConditionalOnProperty(name = "agent.provider", havingValue = "STUB")
  ModelPort stubModel(Environment env) {
    if (Arrays.stream(env.getActiveProfiles())
        .noneMatch(p -> p.equals("local") || p.equals("test")))
      throw new IllegalStateException("STUB is allowed only in local/test profiles");
    return new StubModel();
  }

  @Bean
  @ConditionalOnProperty(name = "agent.mcp.enabled", havingValue = "false", matchIfMissing = true)
  PolicyGateway embeddedPolicy() {
    return topic -> {
      if (!"service_credit".equals(topic)) throw new IllegalArgumentException("Unsupported policy");
      return PolicyGateway.policy();
    };
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "agent.mcp.enabled", havingValue = "true")
  McpPolicyGateway mcpPolicy(AgentProperties config) {
    return new McpPolicyGateway(config.mcp());
  }

  @Bean(destroyMethod = "shutdown")
  ExecutorService streamExecutor(AgentProperties config) {
    return new ThreadPoolExecutor(
        config.concurrency(),
        config.concurrency(),
        0,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        r -> new Thread(r, "agent-sse"),
        new ThreadPoolExecutor.AbortPolicy());
  }
}
