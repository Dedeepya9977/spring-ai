package com.dedeepya.agent.infrastructure.openai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

public final class ModelTimingAdvisor implements CallAdvisor, StreamAdvisor {
  private final Timer timer;

  public ModelTimingAdvisor(MeterRegistry meterRegistry) {
    this.timer =
        Timer.builder("assistant.model.latency")
            .description("Time spent waiting for a model response")
            .register(meterRegistry);
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    long started = System.nanoTime();
    try {
      return chain.nextCall(request);
    } finally {
      timer.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }
  }

  @Override
  public Flux<ChatClientResponse> adviseStream(
      ChatClientRequest request, StreamAdvisorChain chain) {
    long started = System.nanoTime();
    return chain
        .nextStream(request)
        .doFinally(signal -> timer.record(System.nanoTime() - started, TimeUnit.NANOSECONDS));
  }

  @Override
  public String getName() {
    return "modelTimingAdvisor";
  }

  @Override
  public int getOrder() {
    return 0;
  }
}
