package com.dedeepya.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dedeepya.agent.api.controller.AssistantController;
import com.dedeepya.agent.api.dto.request.AskAssistantRequest;
import com.dedeepya.agent.application.service.impl.AssistantServiceImpl;
import com.dedeepya.agent.application.usecase.AskAssistantUseCase;
import com.dedeepya.agent.domain.model.AssistantReply;
import com.dedeepya.agent.domain.port.out.LanguageModelPort;
import org.junit.jupiter.api.Test;

class AssistantControllerTest {
  @Test
  void mapsRequestAndDomainReplyToResponseDto() {
    LanguageModelPort model =
        prompt -> new AssistantReply("done", "test", "test-model");

    var controller =
        new AssistantController(new AskAssistantUseCase(new AssistantServiceImpl(model)));

    var response = controller.ask(new AskAssistantRequest("hello"));

    assertThat(response.answer()).isEqualTo("done");
    assertThat(response.provider()).isEqualTo("test");
    assertThat(response.model()).isEqualTo("test-model");
  }
}
