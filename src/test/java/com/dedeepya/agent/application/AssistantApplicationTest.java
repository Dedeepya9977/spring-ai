package com.dedeepya.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dedeepya.agent.application.service.impl.AssistantServiceImpl;
import com.dedeepya.agent.application.usecase.AskAssistantUseCase;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;
import com.dedeepya.agent.domain.port.out.LanguageModelPort;
import org.junit.jupiter.api.Test;

class AssistantApplicationTest {
  @Test
  void useCaseDelegatesToApplicationServiceAndReturnsDomainReply() {
    LanguageModelPort model =
        prompt -> new AssistantReply("answer for " + prompt.message(), "test", "test-model");

    var useCase = new AskAssistantUseCase(new AssistantServiceImpl(model));

    assertThat(useCase.execute(new AssistantPrompt("hello")))
        .isEqualTo(new AssistantReply("answer for hello", "test", "test-model"));
  }
}
