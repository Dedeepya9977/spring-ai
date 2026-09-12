package com.dedeepya.agent.engine;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.exception.ApiException;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.stereotype.Component;

@Component
public class BudgetPolicy {
  private final AgentProperties config;
  private final ContextPolicy context;

  public BudgetPolicy(AgentProperties config, ContextPolicy context) {
    this.config = config;
    this.context = context;
  }

  public void reserve(RunState state, Instant deadline) {
    checkTime(deadline);
    if (state.steps >= config.maxSteps())
      throw ApiException.bad("STEP_LIMIT", "Agent reached its step limit");
    context.checkCurrent(state);
    long input = context.inputUpperBound(state), tokens = input + config.maxOutputTokens();
    // Reserve for TWO possible network attempts. Unknown failed-attempt usage stays reserved.
    long reserve = 2 * tokens;
    BigDecimal cost = estimate(input, 0, config.maxOutputTokens()).multiply(BigDecimal.valueOf(2));
    if (state.reservedTokens + reserve > config.maxRunTokens()
        || state.reservedCost.add(cost).compareTo(config.maxRunCostUsd()) > 0)
      throw ApiException.bad(
          "BUDGET_LIMIT", "Run would exceed its configured token or cost budget");
    state.reservedTokens += reserve;
    state.reservedCost = state.reservedCost.add(cost);
    state.steps++;
  }

  public void record(RunState state, ModelPort.Result result) {
    state.inputTokens += result.inputTokens();
    state.cachedInputTokens += result.cachedInputTokens();
    state.outputTokens += result.outputTokens();
    state.estimatedCost =
        state.estimatedCost.add(
            estimate(result.inputTokens(), result.cachedInputTokens(), result.outputTokens()));
  }

  public BigDecimal estimate(long input, long cached, long output) {
    long safeCached = Math.min(Math.max(0, cached), Math.max(0, input));
    return config
        .inputUsdPerMillion()
        .multiply(BigDecimal.valueOf(Math.max(0, input - safeCached)))
        .add(config.cachedInputUsdPerMillion().multiply(BigDecimal.valueOf(safeCached)))
        .add(config.outputUsdPerMillion().multiply(BigDecimal.valueOf(Math.max(0, output))))
        .movePointLeft(6);
  }

  public static void checkTime(Instant deadline) {
    if (!Instant.now().isBefore(deadline) || Thread.currentThread().isInterrupted())
      throw ApiException.bad("DEADLINE_EXCEEDED", "Run deadline exceeded");
  }
}
