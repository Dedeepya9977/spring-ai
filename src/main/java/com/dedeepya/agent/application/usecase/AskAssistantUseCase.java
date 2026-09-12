package com.dedeepya.agent.application.usecase;

import com.dedeepya.agent.application.service.AssistantService;
import com.dedeepya.agent.application.service.PromptService;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AskAssistantUseCase {
  private final AssistantService assistantService;
  private final PromptService promptService;

  @Autowired
  public AskAssistantUseCase(AssistantService assistantService, PromptService promptService) {
    this.assistantService = assistantService;
    this.promptService = promptService;
  }

  public AskAssistantUseCase(AssistantService assistantService) {
    this(assistantService, (message, history) -> new AssistantPrompt(message));
  }

  public AssistantReply execute(String message) {
    return assistantService.generate(promptService.build(message, List.of()));
  }

  public AssistantReply execute(AssistantPrompt prompt) {
    return assistantService.generate(prompt);
  }
}
