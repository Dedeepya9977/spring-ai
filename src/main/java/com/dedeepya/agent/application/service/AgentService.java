package com.dedeepya.agent.application.service;

import com.dedeepya.agent.application.usecase.RunAgentUseCase;
import com.dedeepya.agent.application.usecase.RunCancellationUseCase;
import com.dedeepya.agent.application.usecase.RunDecisionUseCase;
import com.dedeepya.agent.application.usecase.RunQueryUseCase;

/** Application use cases shared by HTTP and streaming entry points. */
public interface AgentService
    extends RunAgentUseCase, RunQueryUseCase, RunDecisionUseCase, RunCancellationUseCase {}
