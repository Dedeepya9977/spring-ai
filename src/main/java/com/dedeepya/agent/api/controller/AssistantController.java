package com.dedeepya.agent.api.controller;

import com.dedeepya.agent.api.dto.request.AskAssistantRequest;
import com.dedeepya.agent.api.dto.response.AskAssistantResponse;
import com.dedeepya.agent.application.usecase.AskAssistantUseCase;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
  private final AskAssistantUseCase askAssistantUseCase;

  public AssistantController(AskAssistantUseCase askAssistantUseCase) {
    this.askAssistantUseCase = askAssistantUseCase;
  }

  @PostMapping
  public AskAssistantResponse ask(@Valid @RequestBody AskAssistantRequest request) {
    var reply = askAssistantUseCase.execute(new AssistantPrompt(request.message()));
    return AskAssistantResponse.from(reply);
  }
}
