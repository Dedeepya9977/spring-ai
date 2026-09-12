package com.dedeepya.agent.infrastructure.openai;

import com.dedeepya.agent.domain.model.Answer;
import com.dedeepya.agent.domain.model.RunState;
import com.dedeepya.agent.domain.port.ModelPort;
import com.dedeepya.agent.engine.Jsons;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Deterministic teaching double. It is NOT a local LLM and NOT a model-quality evaluation. */
public class StubModel implements ModelPort {
  @Override
  public Result generate(
      RunState state, boolean toolsEnabled, Instant deadline, Consumer<String> delta) {
    String user = "";
    for (var item : state.transcript)
      if ("user".equals(item.get("role")) && !Jsons.write(item).contains("untrusted_order_data"))
        user = Jsons.write(item);
    var match = Pattern.compile("ORD-[0-9]{4,10}").matcher(user);
    String order = match.find() ? match.group() : state.orderId;
    if (order == null)
      return answer(
          new Answer(
              "Please provide an order ID such as ORD-1001.",
              null,
              Answer.Action.ASK_DETAILS,
              List.of()),
          delta);
    if (toolsEnabled && !state.evidence.contains("order:" + order) && state.steps == 1)
      return call("lookup_order", Jsons.write(Map.of("orderId", order)));
    boolean credit = user.toLowerCase(Locale.ROOT).contains("credit");
    if (toolsEnabled
        && credit
        && state.evidence.contains("order:" + order)
        && !state.evidence.contains("policy:service_credit:v1"))
      return call("search_policy", Jsons.write(Map.of("topic", "service_credit")));
    if (toolsEnabled
        && credit
        && !state.creditRecorded
        && state.steps <= 3
        && state.evidence.contains("order:" + order))
      return call(
          "propose_credit",
          Jsons.write(
              Map.of(
                  "orderId",
                  order,
                  "amountPaise",
                  10000,
                  "reason",
                  "Synthetic delayed service request")));
    if (state.creditRecorded)
      return answer(
          new Answer(
              "A service credit was recorded in the local ledger after human approval.",
              order,
              Answer.Action.CREDIT_RECORDED,
              List.copyOf(state.evidence)),
          delta);
    if (!state.evidence.contains("order:" + order))
      return answer(
          new Answer(
              "I could not find an accessible order. Please check the ID.",
              null,
              Answer.Action.ESCALATE,
              List.of()),
          delta);
    return answer(
        new Answer(
            credit
                ? "The request was reviewed; no new credit was recorded."
                : "The requested order was found. See the referenced order record for its status.",
            order,
            Answer.Action.ANSWER,
            List.copyOf(state.evidence)),
        delta);
  }

  public static Result call(String name, String args) {
    String id = "call_" + UUID.randomUUID();
    return new Result(
        Outcome.TOOLS,
        List.of(Map.of("type", "function_call", "call_id", id, "name", name, "arguments", args)),
        List.of(new ToolCall(id, name, args)),
        "",
        100,
        0,
        20);
  }

  public static Result answer(Answer answer, Consumer<String> delta) {
    String text = Jsons.write(answer);
    for (int i = 0; i < text.length(); i += 24)
      delta.accept(text.substring(i, Math.min(text.length(), i + 24)));
    return new Result(
        Outcome.COMPLETED,
        List.of(
            Map.of(
                "role",
                "assistant",
                "type",
                "message",
                "status",
                "completed",
                "content",
                List.of(Map.of("type", "output_text", "text", text, "annotations", List.of())))),
        List.of(),
        text,
        100,
        0,
        50);
  }
}
