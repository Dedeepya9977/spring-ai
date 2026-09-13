package com.dedeepya.agent.domain.port;

import com.dedeepya.agent.domain.model.RunState;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

public interface ModelPort {
  record ToolCall(String id, String name, String arguments) {}

  enum Outcome {
    COMPLETED,
    TOOLS,
    REFUSED,
    INCOMPLETE,
    FAILED
  }

  record Result(
      Outcome outcome,
      List<Map<String, Object>> outputItems,
      List<ToolCall> calls,
      String text,
      long inputTokens,
      long cachedInputTokens,
      long outputTokens) {}

  Result generate(RunState state, boolean toolsEnabled, Instant deadline, Consumer<String> delta);
}
