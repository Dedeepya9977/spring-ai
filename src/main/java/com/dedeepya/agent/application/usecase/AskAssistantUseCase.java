package com.dedeepya.agent.application.usecase;

import com.dedeepya.agent.application.service.AssistantService;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;
import org.springframework.stereotype.Component;

@Component
public class AskAssistantUseCase {
  private final AssistantService assistantService;

  public AskAssistantUseCase(AssistantService assistantService) {
    this.assistantService = assistantService;
  }

  public AssistantReply execute(AssistantPrompt prompt) {
    return assistantService.generate(prompt);
  }
}
