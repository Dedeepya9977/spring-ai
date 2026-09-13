# Chunk 2: Prompt and conversation boundary

This chunk introduces an explicit prompt boundary for the assistant slice.

## What changed

- ConversationMessage and ConversationRole model conversation data without provider dependencies.
- AssistantPrompt carries the current user message, optional history, and system instruction.
- PromptProperties keeps the system instruction in external configuration.
- PromptService builds the domain prompt before the model adapter is called.
- The OpenAI adapter converts the domain messages into the official SDK response-input types.
- The existing Spring AI runtime has a compatibility adapter, while the OpenAI Java SDK remains selectable with AI_PROVIDER=OPENAI.

## Request flow

    HTTP request
        -> AssistantController
        -> AskAssistantUseCase
        -> PromptService
        -> AssistantService
        -> LanguageModelPort
        -> OpenAiLanguageModelAdapter or SpringAiLanguageModelAdapter

## Why this boundary exists

A controller should not know how a provider represents messages. The domain should not import an SDK type. The application layer should decide what prompt is sent, while infrastructure decides how that prompt is serialized for a provider.

This gives us a stable place to add prompt templates, history limits, token budgeting, structured output, and provider-specific adapters in later chunks.

## Run with the direct OpenAI adapter

Set the provider and API key:

    export AI_PROVIDER=OPENAI
    export OPENAI_API_KEY=your-key
    ./mvnw spring-boot:run

Then call:

    curl -X POST http://localhost:8080/api/v1/assistant       -H 'Content-Type: application/json'       -d '{"message":"Explain what a token is."}'

For local work without a provider key, use the existing local profile:

    ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

## Learning outcome

The main distinction is:

- Prompt construction belongs to the application boundary.
- Provider serialization belongs to infrastructure.
- DTOs belong to the API boundary.
- Domain models remain independent of Spring and provider SDKs.
