package com.dedeepya.agent.mcp;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.engine.Jsons;
import com.dedeepya.agent.tools.PolicyGateway;
import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.*;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class McpPolicyGateway implements PolicyGateway, AutoCloseable {
  private final McpSyncClient client;

  public McpPolicyGateway(AgentProperties.Mcp config) {
    Path jar = Path.of(config.serverJar()).toAbsolutePath().normalize();
    if (!Files.isRegularFile(jar))
      throw new IllegalStateException("MCP server jar is missing; package the application first");
    var params =
        ServerParameters.builder(config.javaCommand())
            .args("-jar", jar.toString(), "--mcp-stdio")
            .env(Map.of())
            .build();
    params.getEnv().clear(); // SDK adds defaults; use only the explicit environment below.
    var transport =
        new StdioClientTransport(params, McpJsonDefaults.getMapper(), 32768) {
          @Override
          protected ProcessBuilder getProcessBuilder() {
            var builder = new ProcessBuilder();
            // Child gets no OpenAI key, DB password, OIDC secret or arbitrary environment.
            var clean = new HashMap<String, String>();
            for (String key : List.of("PATH", "SystemRoot", "WINDIR", "TMPDIR", "TEMP")) {
              String value = System.getenv(key);
              if (value != null) clean.put(key, value);
            }
            builder.environment().clear();
            builder.environment().putAll(clean);
            return builder;
          }
        };
    client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(5)).build();
    try {
      client.initialize();
      var listed = client.listTools().tools();
      if (listed.size() != 1 || !"search_policy".equals(listed.get(0).name()))
        throw new IllegalStateException("Unexpected MCP tool catalog");
    } catch (RuntimeException ex) {
      client.closeGracefully();
      throw ex;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> lookup(String topic) {
    if (!"service_credit".equals(topic)) throw new IllegalArgumentException("Unknown policy topic");
    var result =
        client.callTool(
            McpSchema.CallToolRequest.builder("search_policy")
                .arguments(Map.of("topic", topic))
                .build());
    if (Boolean.TRUE.equals(result.isError())) throw new IllegalStateException("MCP tool failed");
    String text =
        result.content().stream()
            .filter(c -> c instanceof McpSchema.TextContent)
            .map(c -> ((McpSchema.TextContent) c).text())
            .findFirst()
            .orElseThrow();
    if (text.length() > 8000) throw new IllegalStateException("MCP result exceeded size limit");
    Map<String, Object> data = Jsons.read(text, LinkedHashMap.class);
    // Remote tool discovery and annotations do not grant execution authority.
    if (!"policy:service_credit:v1".equals(data.get("evidenceId")))
      throw new IllegalStateException("Unexpected policy identity");
    return data;
  }

  public McpSchema.ReadResourceResult readPolicyResource() {
    return client.readResource(
        McpSchema.ReadResourceRequest.builder("policy://service-credit/v1").build());
  }

  @Override
  public void close() {
    client.closeGracefully();
  }
}
