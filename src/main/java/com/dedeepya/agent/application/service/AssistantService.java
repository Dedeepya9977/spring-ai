package com.dedeepya.agent.application.service;

import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;

public interface AssistantService {
  AssistantReply generate(AssistantPrompt prompt);
}
