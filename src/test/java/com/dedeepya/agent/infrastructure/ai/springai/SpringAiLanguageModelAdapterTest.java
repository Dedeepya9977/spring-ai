package com.dedeepya.agent.infrastructure.ai.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dedeepya.agent.Fixtures;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.ConversationMessage;
import com.dedeepya.agent.engine.ModelPort;
import com.dedeepya.agent.engine.RunState;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SpringAiLanguageModelAdapterTest {
  @Test
  void forwardsSystemInstructionHistoryAndCurrentMessage() {
    var captured = new AtomicReference<RunState>();
    ModelPort model =
        (state, toolsEnabled, deadline, delta) -> {
          captured.set(state);
          return new ModelPort.Result(
              ModelPort.Outcome.COMPLETED, List.of(), List.of(), "done", 1, 0, 1);
        };

    var adapter = new SpringAiLanguageModelAdapter(model, Fixtures.config());
    var reply =
        adapter.complete(
            new AssistantPrompt(
                "What is the order status?",
                "Follow the support policy.",
                List.of(
                    ConversationMessage.user("The order is ORD-1001."),
                    ConversationMessage.assistant("I will check that."))));

    assertThat(reply.answer()).isEqualTo("done");
    assertThat(captured.get().transcript)
        .containsExactly(
            java.util.Map.of("role", "system", "content", "Follow the support policy."),
            java.util.Map.of("role", "user", "content", "The order is ORD-1001."),
            java.util.Map.of("role", "assistant", "content", "I will check that."),
            java.util.Map.of("role", "user", "content", "What is the order status?"));
  }

  @Test
  void acceptsSystemMessagesWhenMappingSpringAiConversation() {
    var state = new RunState();
    state.transcript.add(java.util.Map.of("role", "system", "content", "Additional policy."));

    assertThatCode(() -> SpringAiModel.messages(state)).doesNotThrowAnyException();
    assertThat(SpringAiModel.messages(state)).hasSize(2);
  }
}
