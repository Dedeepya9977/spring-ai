package com.dedeepya.agent.tools;

import com.dedeepya.agent.domain.model.RunState;
import com.dedeepya.agent.domain.port.ModelPort;
import com.dedeepya.agent.domain.port.PolicyGateway;
import com.dedeepya.agent.engine.Jsons;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ToolExecutor {
  public record Reply(
      boolean ok,
      Map<String, Object> data,
      String errorCategory,
      boolean isRetryable,
      String feedback) {
    public static Reply ok(Map<String, Object> data) {
      return new Reply(true, data, null, false, null);
    }

    public static Reply error(String category, boolean retryable, String feedback) {
      return new Reply(false, Map.of(), category, retryable, feedback);
    }
  }

  private final JdbcTemplate jdbc;
  private final PolicyGateway policy;

  public ToolExecutor(JdbcTemplate jdbc, PolicyGateway policy) {
    this.jdbc = jdbc;
    this.policy = policy;
  }

  public Reply executeRead(String tenant, ModelPort.ToolCall call) {
    ToolCatalog.Arguments args;
    try {
      args = ToolCatalog.validate(call.name(), call.arguments());
    } catch (IllegalArgumentException ex) {
      return Reply.error("INVALID_ARGUMENTS", false, ex.getMessage());
    }
    return switch (call.name()) {
      case "lookup_order" -> lookupOrder(tenant, args.orderId());
      case "search_policy" -> {
        try {
          yield Reply.ok(policy.lookup(args.topic()));
        } catch (RuntimeException ex) {
          yield Reply.error(
              "UPSTREAM_UNAVAILABLE",
              true,
              "Policy lookup unavailable; ask for help or retry within the remaining budget");
        }
      }
      default ->
          Reply.error(
              "PERMISSION_DENIED", false, "This tool requires a durable human approval gate");
    };
  }

  public Reply lookupOrder(String tenant, String orderId) {
    var rows =
        jdbc.query(
            "SELECT status,total_paise FROM service_orders WHERE tenant=? AND order_id=?",
            (rs, n) ->
                Map.<String, Object>of(
                    "orderId",
                    orderId,
                    "status",
                    rs.getString(1),
                    "totalPaise",
                    rs.getLong(2),
                    "evidenceId",
                    "order:" + orderId),
            tenant,
            orderId);
    return rows.isEmpty()
        ? Reply.error("NOT_FOUND", false, "Order not found in your accessible records")
        : Reply.ok(rows.get(0));
  }

  public Reply validateProposal(String tenant, ModelPort.ToolCall call) {
    ToolCatalog.Arguments args;
    try {
      args = ToolCatalog.validate(call.name(), call.arguments());
    } catch (IllegalArgumentException ex) {
      return Reply.error("INVALID_ARGUMENTS", false, ex.getMessage());
    }
    Reply order = lookupOrder(tenant, args.orderId());
    if (!order.ok()) return order;
    if (!"DELAYED".equals(order.data().get("status"))
        || args.amountPaise() > (long) order.data().get("totalPaise"))
      return Reply.error(
          "BUSINESS_RULE", false, "Order is ineligible or amount exceeds order value");
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_credits WHERE tenant=? AND order_id=?",
            Long.class,
            tenant,
            args.orderId())
        > 0)
      return Reply.error(
          "BUSINESS_RULE", false, "A credit has already been recorded for this order");
    return order;
  }

  public static void appendResult(RunState state, String callId, Reply reply) {
    if (reply.ok() && reply.data().get("evidenceId") instanceof String evidence)
      state.evidence.add(evidence);
    state.transcript.add(
        Map.of("type", "function_call_output", "call_id", callId, "output", Jsons.write(reply)));
  }
}
