package com.dedeepya.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dedeepya.agent.application.service.impl.PromptServiceImpl;
import com.dedeepya.agent.config.PromptProperties;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.ConversationMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptServiceTest {
  @Test
  void buildsPromptWithConfiguredInstructionsAndConversationHistory() {
    var service = new PromptServiceImpl(new PromptProperties("Follow the support policy."));

    AssistantPrompt prompt =
        service.build(
            "What is the order status?",
            List.of(ConversationMessage.user("My order is ORD-1001.")));

    assertThat(prompt.systemInstruction()).isEqualTo("Follow the support policy.");
    assertThat(prompt.history()).containsExactly(ConversationMessage.user("My order is ORD-1001."));
    assertThat(prompt.inputMessages())
        .containsExactly(
            ConversationMessage.user("My order is ORD-1001."),
            ConversationMessage.user("What is the order status?"));
  }
}
