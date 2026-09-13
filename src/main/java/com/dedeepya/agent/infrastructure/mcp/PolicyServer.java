package com.dedeepya.agent.infrastructure.mcp;

import com.dedeepya.agent.domain.port.PolicyGateway;
import com.dedeepya.agent.engine.Jsons;
import com.dedeepya.agent.tools.ToolCatalog;
import com.dedeepya.agent.tools.ToolExecutor;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Same executable jar, separate process: java -jar app.jar --mcp-stdio. */
public final class PolicyServer {
  private PolicyServer() {}

  public static void serve() throws InterruptedException {
    var mapper = McpJsonDefaults.getMapper();
    var transport = new StdioServerTransportProvider(mapper);
    var definition =
        ToolCatalog.definitions().stream()
            .filter(d -> d.name().equals("search_policy"))
            .findFirst()
            .orElseThrow();
    var tool =
        McpSchema.Tool.builder(definition.name(), definition.schema())
            .description(definition.description())
            .build();
    var spec =
        McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(
                (exchange, request) -> {
                  try {
                    ToolCatalog.validate("search_policy", Jsons.write(request.arguments()));
                    return McpSchema.CallToolResult.builder()
                        .addTextContent(Jsons.write(PolicyGateway.policy()))
                        .structuredContent(PolicyGateway.policy())
                        .isError(false)
                        .build();
                  } catch (IllegalArgumentException ex) {
                    var error =
                        ToolExecutor.Reply.error("INVALID_ARGUMENTS", false, ex.getMessage());
                    return McpSchema.CallToolResult.builder()
                        .addTextContent(Jsons.write(error))
                        .isError(true)
                        .build();
                  }
                })
            .build();
    String uri = "policy://service-credit/v1";
    var resource =
        McpSchema.Resource.builder(uri, "Service credit policy")
            .mimeType("application/json")
            .build();
    var resourceSpec =
        new McpServerFeatures.SyncResourceSpecification(
            resource,
            (exchange, request) ->
                McpSchema.ReadResourceResult.builder(
                        List.of(
                            new McpSchema.TextResourceContents(
                                uri,
                                "application/json",
                                Jsons.write(PolicyGateway.policy()),
                                null)))
                    .build());
    var server =
        McpServer.sync(transport)
            .serverInfo("automotive-policy", "1.0.0")
            .capabilities(
                McpSchema.ServerCapabilities.builder().tools(false).resources(false, false).build())
            .tools(spec)
            .resources(resourceSpec)
            .build();
    Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
    new CountDownLatch(1).await();
  }
}
