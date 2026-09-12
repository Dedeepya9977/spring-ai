package com.dedeepya.agent.application.service.impl;

import com.dedeepya.agent.application.service.AssistantService;
import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;
import com.dedeepya.agent.domain.port.out.LanguageModelPort;
import org.springframework.stereotype.Service;

@Service
public class AssistantServiceImpl implements AssistantService {
  private final LanguageModelPort languageModelPort;

  public AssistantServiceImpl(LanguageModelPort languageModelPort) {
    this.languageModelPort = languageModelPort;
  }

  @Override
  public AssistantReply generate(AssistantPrompt prompt) {
    return languageModelPort.complete(prompt);
  }
}
