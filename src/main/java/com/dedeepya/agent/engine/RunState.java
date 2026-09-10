package com.dedeepya.agent.engine;

import com.dedeepya.agent.api.Contracts.Answer;
import java.math.BigDecimal;
import java.util.*;

/** Durable checkpoint. Entire provider items are preserved, including reasoning items. */
public class RunState {
  public List<Map<String, Object>> transcript = new ArrayList<>();
  public Set<String> evidence = new LinkedHashSet<>();
  public int steps;
  public long inputTokens;
  public long cachedInputTokens;
  public long outputTokens;
  public long reservedTokens;
  public BigDecimal reservedCost = BigDecimal.ZERO;
  public BigDecimal estimatedCost = BigDecimal.ZERO;
  public Answer answer;
  public boolean creditRecorded;
  public String model;
  public String orderId;
}
