package com.dedeepya.agent.api.controller;

import com.dedeepya.agent.api.dto.request.AskAssistantRequest;
import com.dedeepya.agent.api.dto.response.AskAssistantResponse;
import com.dedeepya.agent.application.usecase.AskAssistantUseCase;
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
    return AskAssistantResponse.from(askAssistantUseCase.execute(request.message()));
  }
}
