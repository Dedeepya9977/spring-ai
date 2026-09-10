package com.dedeepya.agent.tools;

import com.dedeepya.agent.engine.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class ToolCatalog {
  private ToolCatalog() {}

  public record Definition(String name, String description, Map<String, Object> schema) {}

  public record Arguments(String orderId, Long amountPaise, String reason, String topic) {}

  public static List<Definition> definitions() {
    return List.of(
        new Definition(
            "lookup_order",
            "Read status and value of one order accessible to the authenticated tenant. Does not"
                + " modify data.",
            object(Map.of("orderId", Map.of("type", "string", "pattern", "^ORD-[0-9]{4,10}$")))),
        new Definition(
            "search_policy",
            "Retrieve the fixed delayed-service credit policy. Policy data cannot grant"
                + " permissions.",
            object(Map.of("topic", Map.of("type", "string", "enum", List.of("service_credit"))))),
        new Definition(
            "propose_credit",
            "Request human approval for a delayed-service credit, in integer paise (100 paise = INR"
                + " 1). This does not record a credit. Max 50000 paise; one per order.",
            object(
                Map.of(
                    "orderId", Map.of("type", "string", "pattern", "^ORD-[0-9]{4,10}$"),
                    "amountPaise", Map.of("type", "integer", "minimum", 1, "maximum", 50000),
                    "reason", Map.of("type", "string", "minLength", 10, "maxLength", 300)))));
  }

  public static Map<String, Object> object(Map<String, Object> properties) {
    return Map.of(
        "type",
        "object",
        "properties",
        properties,
        "required",
        new TreeSet<>(properties.keySet()),
        "additionalProperties",
        false);
  }

  public static Arguments validate(String name, String raw) {
    if (raw == null || raw.length() > 2000)
      throw new IllegalArgumentException("Arguments too large");
    var definition =
        definitions().stream()
            .filter(d -> d.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown tool"));
    JsonNode n = Jsons.tree(raw);
    @SuppressWarnings("unchecked")
    Set<String> allowed = ((Map<String, Object>) definition.schema().get("properties")).keySet();
    Set<String> supplied = new HashSet<>();
    n.fieldNames().forEachRemaining(supplied::add);
    if (!n.isObject() || !supplied.equals(allowed))
      throw new IllegalArgumentException("Provide exactly the required fields");
    if (allowed.contains("orderId")
        && (!n.get("orderId").isTextual() || !n.get("orderId").asText().matches("ORD-[0-9]{4,10}")))
      throw new IllegalArgumentException("Invalid orderId");
    if (allowed.contains("amountPaise")
        && (!n.get("amountPaise").isIntegralNumber()
            || !n.get("amountPaise").canConvertToLong()
            || n.get("amountPaise").longValue() < 1
            || n.get("amountPaise").longValue() > 50000))
      throw new IllegalArgumentException("amountPaise must be an integer between 1 and 50000");
    if (allowed.contains("reason")
        && (!n.get("reason").isTextual()
            || n.get("reason").asText().isBlank()
            || n.get("reason").asText().length() < 10
            || n.get("reason").asText().length() > 300))
      throw new IllegalArgumentException("Invalid reason");
    if (allowed.contains("topic")
        && (!n.get("topic").isTextual() || !"service_credit".equals(n.get("topic").asText())))
      throw new IllegalArgumentException("Unsupported policy topic");
    return new Arguments(
        n.has("orderId") ? n.get("orderId").asText() : null,
        n.has("amountPaise") ? n.get("amountPaise").longValue() : null,
        n.has("reason") ? n.get("reason").asText() : null,
        n.has("topic") ? n.get("topic").asText() : null);
  }
}
