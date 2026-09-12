# Spring AI in this Java project

## What is implemented now?

The default real provider is `AI_PROVIDER=SPRING_AI`. The request passes through `AgentServiceImpl` → `SpringAiModel` → Spring AI `ChatClient` → `OpenAiChatModel` → the official OpenAI Java SDK → Chat Completions. The local profile still uses the no-key scripted `STUB`.

`pom.xml` pins Spring Boot **4.1.1** and the Spring AI BOM **2.0.1**. Spring documents support for Boot 4.0.x/4.1.x in Spring AI 2.0.x. Explicit `spring-ai-openai` and `spring-ai-client-chat` modules are configured in `RuntimeConfiguration`; provider construction is deliberately conditional so local mode needs no API key. [Spring AI getting started](https://docs.spring.io/spring-ai/reference/getting-started.html).

Spring AI's OpenAI model uses the official Java SDK internally. This project also keeps `AI_PROVIDER=OPENAI` for the direct Responses API adapter. Create a new session when switching adapters: Responses encrypted reasoning items are not Chat Completions messages. [Spring AI OpenAI integration](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html).

The Java application uses `ChatClient`, `Prompt`, `SystemMessage`, `UserMessage`, `AssistantMessage`, `ToolResponseMessage`, `Media`, `OpenAiChatOptions`, strict tool definitions, structured-output options, streaming, and usage metadata. Business validation, persistence, approvals, and token/cost reservations remain in the application services.

## Concept map

| In this project | Spring AI concept to learn | What you still must enforce |
| --- | --- | --- |
| `SpringAiModel` / `OpenAiChatModel` | Implemented `ChatModel` and `ChatClient` | Provider options, allowed models, timeout/usage policy |
| `ContextPolicy.SYSTEM` | System prompt / `PromptTemplate` | Trust boundaries; safe interpolation |
| `RunState.transcript` | `ChatMemory`, memory repository and memory advisor | Tenant/owner isolation and complete tool exchanges |
| `OutputPolicy` | Structured output/entity conversion | Semantic/business validation and evidence checks |
| `ToolCatalog` | Implemented schema callbacks; `@Tool` is a later lab | Exact arguments, identity, least privilege, authorization |
| `AgentServiceImpl` checks | Advisors and application services | Mandatory controls must not be bypassable by the model |
| `PolicyGateway` | Tool callback or MCP integration | Server trust, tool allowlist, result bounds |
| `SpringAiModel.generate` | Implemented `ChatClient.stream()` | Cancellation, terminal state, provisional content handling |
| `BudgetPolicy` and Micrometer | Response usage metadata and observations | Real budgets, rate card, per-tenant limits |
| Fixed policy retrieval | Later `VectorStore` + retrieval advisor | Ingestion quality, ACL filtering, evidence, evaluation |

## Lab A — Trace the existing `ChatClient` integration

**Goal:** Understand the compiled Java path before adding another endpoint.

1. Open `RuntimeConfiguration.springAiModel`. It injects the official SDK client, applies server-controlled configuration, and constructs `OpenAiChatModel`.
2. Open `SpringAiModel.generate`. Find `chatClient.prompt(new Prompt(...)).stream().chatResponse()` and the timeout around stream completion.
3. Follow `messages(...)`: stored user/assistant/tool exchanges become typed Spring AI messages. Tool response IDs stay associated with their original calls.
4. Read the native JSON schema, `store(false)`, one-tool-at-a-time setting, response usage, and finish-reason mapping.
5. Run `./mvnw -Dtest=SpringAiModelTest test` (PowerShell: `.\mvnw.cmd "-Dtest=SpringAiModelTest" test`). These tests use a local HTTP server and cost nothing.

**Critical detail:** Spring AI 2 automatically registers a tool-calling advisor. This project sets `ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER` to `false`. Its callbacks supply schemas and throw if automatically invoked. A completed tool request returns to `AgentServiceImpl`, which validates it and either executes an authorized read or records a durable human approval request. Never enable automatic tool execution for the credit workflow.

**Exercise:** Compare the Spring AI wire test's `/v1/chat/completions` request with the direct SDK test's `/v1/responses` request. Identify the differing history, tool-result, status, and usage fields. Neither adapter automatically converts Claude stop reasons.

**Success condition:** You can trace a streaming answer and a credit proposal through Spring AI without bypassing the approval state machine. [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html).

## Lab B — Structured output and memory

**Goal:** Learn conversion and history independently.

1. Return a simple Java record such as `Explanation(String concept, String description)` using Spring AI's structured output support.
2. Test missing fields, unexpected values, refusal, and truncation. Parsing to a record does not prove the answer is factually correct.
3. Add a memory implementation and a conversation identifier.
4. Associate the identifier with the authenticated tenant and owner on the server; never let a user select another user's conversation by guessing a header.
5. Compare a repeated question in the same conversation with a new conversation.

Memory policies choose what to send to the model. They are not necessarily a complete conversation archive or a guarantee of tool-call history fidelity; read the limitations for the implementation/version you choose. [Spring AI chat memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html).

**Success condition:** Your tests show that one tenant's history cannot enter another tenant's prompt, and you can explain the difference between stored history and selected model context.

## Lab C — A read-only `@Tool` and advisors

**Goal:** Understand how Spring AI exposes Java functions.

1. Expose the fixed `PolicyGateway.policy()` data as a read-only tool. Give it one clear description.
2. Observe the schema generated for its method parameters and compare it with `ToolCatalog`.
3. Add a deliberately confusing second tool in a learning branch, then evaluate whether routing quality degrades.
4. Inspect the configured tool-execution lifecycle and how identity/context is passed to the callback.
5. Add a simple advisor that records a safe timing metric; keep prompt contents and keys out of logs.

Spring AI offers tool declarations/callbacks, but permissions and action validation remain application decisions. Never annotate `RunStore.decide` as an automatically callable write tool; it requires an authenticated human decision. [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/api/tools.html).

**Success condition:** You can show the model asking for the policy, Java executing the read, and the result returning to the model, while proving there is no automatic credit-write path.

## Lab D — RAG with PostgreSQL/pgvector

**Goal:** Answer questions from a small document set with traceable evidence.

This is a planned extension, not a feature already present in the main app.

1. Create three short, synthetic service-policy documents. Give each a stable document ID, version, and tenant classification.
2. Split each document into meaningful chunks. Preserve document/section metadata.
3. Generate embeddings using an embedding model; verify its output dimension before defining the vector column.
4. Store chunks, metadata and vectors in pgvector through a supported `VectorStore` integration.
5. For each user query, apply tenant/visibility filtering during retrieval and retrieve a small set of relevant chunks.
6. Send those chunks as untrusted context, require evidence IDs, and validate that citations refer to the retrieved set.
7. Add an “insufficient evidence” path.
8. Evaluate retrieval quality separately from answer quality: relevant chunk found, unsupported claim rate, citation correctness, latency and token cost.

Retrieval-augmented generation supplies selected external information at request time. It is separate from model training and conversation memory. Spring AI has retrieval/advisor components that can support this flow. [Spring AI RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html).

**Success condition:** A wrong-tenant document is never retrieved, a missing policy produces uncertainty, and an answer cites the exact supplied policy version.

## Lab E — Preserve the existing guarantees when changing adapters

Do not replace the direct adapter and assume the rest is equivalent. Verify these behaviors for the chosen Spring AI provider and API:

- Complete versus incomplete/refused output is still detected.
- Real usage metadata reaches budget accounting.
- Provider retries and advisor/tool retries do not multiply into an unbounded attempt count.
- Tools cannot execute automatically before the application approval gate.
- Tool call IDs and required reasoning/history items survive multi-turn requests.
- Stream disconnects do not replay a write or accept an incomplete answer.
- Selected model, JSON schema, caching, multimodal input, and privacy options are supported by that provider integration.

Run the existing security/lifecycle tests against the new adapter boundary, then add provider wire tests and fresh live evaluations. A common abstraction does not make all provider-specific features identical.

## What you can honestly claim afterward

After understanding and exercising the main project: “Built a Spring Boot application using Spring AI and the OpenAI Java SDK, with streaming, validated tool calls, durable approvals, MCP and contract tests.”

After completing additional labs, name only the memory, advisor, annotation, or RAG work you actually implemented and evaluated. The shipped application does not include a vector store or RAG ingestion pipeline.
