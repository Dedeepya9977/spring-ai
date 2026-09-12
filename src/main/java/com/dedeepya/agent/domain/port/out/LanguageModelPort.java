package com.dedeepya.agent.domain.port.out;

import com.dedeepya.agent.domain.model.AssistantPrompt;
import com.dedeepya.agent.domain.model.AssistantReply;

public interface LanguageModelPort {
  AssistantReply complete(AssistantPrompt prompt);
}
