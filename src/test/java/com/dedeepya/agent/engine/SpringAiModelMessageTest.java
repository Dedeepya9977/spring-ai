package com.dedeepya.agent.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SpringAiModelMessageTest {
  @Test
  void acceptsSystemMessagesWhenMappingSpringAiConversation() {
    var state = new RunState();
    state.transcript.add(Map.of("role", "system", "content", "Additional policy."));

    assertThatCode(() -> SpringAiModel.messages(state)).doesNotThrowAnyException();
    assertThat(SpringAiModel.messages(state)).hasSize(2);
  }
}
