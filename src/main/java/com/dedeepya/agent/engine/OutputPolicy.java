package com.dedeepya.agent.engine;

import com.dedeepya.agent.api.ApiException;
import com.dedeepya.agent.api.Contracts.Answer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OutputPolicy {
  public Answer validate(String json, RunState state) {
    try {
      var node = Jsons.tree(json);
      if (!node.isObject()
          || node.size() != 4
          || !node.has("summary")
          || !node.has("orderId")
          || !node.has("recommendedAction")
          || !node.has("evidence")) throw new IllegalArgumentException();
      Answer answer = Jsons.read(json, Answer.class);
      if (answer.summary() == null
          || answer.summary().isBlank()
          || answer.summary().length() > 2000
          || answer.recommendedAction() == null
          || answer.evidence() == null
          || answer.evidence().size() > 10
          || answer.evidence().stream().anyMatch(e -> e == null || !state.evidence.contains(e)))
        throw new IllegalArgumentException();
      if (answer.orderId() != null && !state.evidence.contains("order:" + answer.orderId()))
        throw new IllegalArgumentException();
      if (answer.recommendedAction() == Answer.Action.CREDIT_RECORDED && !state.creditRecorded)
        throw new IllegalArgumentException();
      if (answer.recommendedAction() == Answer.Action.ANSWER && answer.evidence().isEmpty())
        throw new IllegalArgumentException();
      return answer;
    } catch (IllegalArgumentException ex) {
      throw ApiException.bad(
          "INVALID_MODEL_OUTPUT", "Model output failed the application contract");
    }
  }

  public static Map<String, Object> schema() {
    return Map.of(
        "type",
        "object",
        "additionalProperties",
        false,
        "properties",
        Map.of(
            "summary", Map.of("type", "string"),
            "orderId", Map.of("type", List.of("string", "null")),
            "recommendedAction",
                Map.of(
                    "type",
                    "string",
                    "enum",
                    List.of("ANSWER", "ASK_DETAILS", "ESCALATE", "CREDIT_RECORDED")),
            "evidence", Map.of("type", "array", "items", Map.of("type", "string"))),
        "required",
        List.of("summary", "orderId", "recommendedAction", "evidence"));
  }
}
