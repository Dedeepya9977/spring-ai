package com.dedeepya.agent.engine;

import static org.assertj.core.api.Assertions.*;

import com.dedeepya.agent.Fixtures;
import com.dedeepya.agent.domain.model.RunState;
import com.dedeepya.agent.dto.RunMode;
import com.dedeepya.agent.dto.request.RunRequest;
import com.dedeepya.agent.dto.response.AnswerResponse;
import com.dedeepya.agent.tools.ToolCatalog;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"orderId\":\"ORD-1001\",\"tenant\":\"other\"}",
        "{\"orderId\":\"ORD-1001\",\"orderId\":\"ORD-9001\"}",
        "{\"orderId\":1001}",
        "{\"orderId\":\"x' OR 1=1 --\"}",
        "{\"orderId\":\"ORD-1001\"} trailing",
        "{}"
      })
  void rejectsMalformedOrInjectedArguments(String json) {
    assertThatThrownBy(() -> ToolCatalog.validate("lookup_order", json))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "50001", "100.5", "\"10000\"", "999999999999999999999"})
  void rejectsInvalidMoney(String amount) {
    assertThatThrownBy(
            () ->
                ToolCatalog.validate(
                    "propose_credit",
                    "{\"orderId\":\"ORD-1001\",\"amountPaise\":"
                        + amount
                        + ",\"reason\":\"Delayed service\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesUnknownTool() {
    assertThatThrownBy(() -> ToolCatalog.validate("execute_sql", "{}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsInventedEvidenceAndCredit() {
    var state = new RunState();
    state.evidence.add("order:ORD-1001");
    var output = new OutputPolicy();
    assertThatThrownBy(
            () ->
                output.validate(
                    Jsons.write(
                        new AnswerResponse(
                            "Done",
                            "ORD-1001",
                            AnswerResponse.Action.ANSWER,
                            List.of("order:ORD-9001"))),
                    state))
        .hasMessageContaining("contract");
    assertThatThrownBy(
            () ->
                output.validate(
                    Jsons.write(
                        new AnswerResponse(
                            "Done",
                            "ORD-1001",
                            AnswerResponse.Action.CREDIT_RECORDED,
                            List.of("order:ORD-1001"))),
                    state))
        .hasMessageContaining("contract");
  }

  @Test
  void rejectsExtraOutputFields() {
    assertThatThrownBy(
            () ->
                new OutputPolicy()
                    .validate(
                        "{\"summary\":\"x\",\"orderId\":null,\"recommendedAction\":\"ASK_DETAILS\",\"evidence\":[],\"debug\":\"secret\"}",
                        new RunState()))
        .hasMessageContaining("contract");
  }

  @Test
  void contextTrimmingPreservesCurrentCallAndResult() {
    var context = new ContextPolicy(Fixtures.config());
    List<Map<String, Object>> items = new ArrayList<>();
    items.add(Map.of("role", "user", "content", "x".repeat(39000)));
    items.add(Map.of("role", "assistant", "content", "Old answer"));
    items.add(Map.of("role", "user", "content", "New question"));
    items.add(
        Map.of(
            "type",
            "function_call",
            "call_id",
            "call1",
            "name",
            "lookup_order",
            "arguments",
            "{}"));
    items.add(
        Map.of("type", "function_call_output", "call_id", "call1", "output", "y".repeat(2000)));
    context.trimCompletedTurns(items);
    assertThat(items).hasSize(3);
    assertThat(items.get(1).get("call_id")).isEqualTo(items.get(2).get("call_id"));
  }

  @Test
  void currentTurnCannotBeSilentlyTruncated() {
    var context = new ContextPolicy(Fixtures.config());
    assertThatThrownBy(
            () ->
                context.trimCompletedTurns(
                    new ArrayList<>(List.of(Map.of("role", "user", "content", "x".repeat(50000))))))
        .hasMessageContaining("too large");
  }

  @Test
  void rejectsRemoteVisionUrl() {
    var request =
        new RunRequest(
            "read image", RunMode.AGENT, null, "http://169.254.169.254/latest/meta-data");
    assertThatThrownBy(() -> new ContextPolicy(Fixtures.config()).addUser(new RunState(), request))
        .hasMessageContaining("remote URLs");
  }

  @Test
  void deadlineAndTokenBudgetFailClosed() {
    var budgets = new BudgetPolicy(Fixtures.config(), new ContextPolicy(Fixtures.config()));
    assertThatThrownBy(() -> budgets.reserve(new RunState(), Instant.now().minusSeconds(1)))
        .hasMessageContaining("deadline");
    var state = new RunState();
    state.reservedTokens = 200000;
    assertThatThrownBy(() -> budgets.reserve(state, Instant.now().plusSeconds(10)))
        .hasMessageContaining("budget");
  }

  @Test
  void cachedTokensAreNotChargedTwice() {
    var budgets = new BudgetPolicy(Fixtures.config(), new ContextPolicy(Fixtures.config()));
    assertThat(budgets.estimate(1000, 500, 100)).isEqualByComparingTo("0.00041");
  }
}
