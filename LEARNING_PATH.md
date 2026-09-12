# Your learning path: AI backend development from the beginning

This path assumes **no AI knowledge**. You can use your Java/Spring Boot experience, but you do not need to understand agents, MCP, RAG, embeddings, or prompt engineering before starting.

The goal is to become able to explain and change this project yourself. You are not expected to memorize the project or read every class on day one.

**Suggested pace:** 24 sessions, roughly 45–90 minutes each, over 4–6 weeks. These are study estimates, not a deadline. Repeat a session until you can do its exercise without copying an answer.

## First answer: does this teach Spring AI?

**Yes. The Java application now uses Spring AI `ChatClient`, `OpenAiChatModel`, typed messages, strict output options, tool definitions, streaming, and usage metadata. Spring AI uses the official OpenAI Java SDK underneath.**

| Layer | What it means | In this project |
| --- | --- | --- |
| Model | The AI system generating an answer or proposing a tool call | A configured OpenAI model in real mode |
| Provider API | The HTTP interface used to access the model | Chat Completions through Spring AI; Responses through the optional direct adapter |
| Provider SDK | Java classes that call that API for you | `com.openai:openai-java` |
| Spring AI | Spring abstractions for models, prompts, tools, memory, retrieval, and more | Compiled runtime dependency; start at `SpringAiModel.java` |
| Your application | Business rules, access control, approvals, persistence, limits | Spring Boot code in this repository |

Read `SpringAiModel.java` alongside `OpenAiModel.java` to compare the Spring AI and direct SDK approaches. [SPRING_AI_BRIDGE.md](docs/SPRING_AI_BRIDGE.md) maps the implemented APIs and the additional memory, annotation, and retrieval exercises.

RAG, pgvector, document ingestion, `ChatMemory`, and custom advisors are **follow-up exercises**. The runtime explicitly disables automatic tool-execution advisor registration so every proposed action returns to the Java approval loop.

## How to study each session

1. Read the short explanation below.
2. Open only the named files in IntelliJ.
3. Run the command or request.
4. Predict what will happen before pressing Run.
5. Make the small change or complete the exercise.
6. Explain the result in three sentences in your own notes.

Keep a `learning-notes.md` file locally. For each session write: “What it does”, “Why it is needed”, and “What breaks without it”. Use synthetic data throughout.

## Stage 0 — Make the application familiar

### Session 1: The basic request journey

**Learn:** A client sends JSON to your Spring controller. Your application can call another HTTP API, then return JSON. An AI API is still an external API: it has credentials, requests, responses, timeouts, and failures.

**Read:** `README.md`, `Application.java`, `controller/AgentController.java`, `dto/request/RunRequest.java and dto/response/RunResponse.java`. Java paths below are relative to `src/main/java/com/dedeepya/agent/` unless stated otherwise.

**Do:** Open `pom.xml` in IntelliJ. Configure JDK 17 or 21 for the project and Maven runner. Run:

```powershell
.\mvnw.cmd verify
```

On Linux/WSL use `./mvnw verify`.

**Done when:** You can find the controller, service, database access code, and external-model adapter. You do not need to understand their contents yet.

### Session 2: Run without paying for an API

**Learn:** A test double returns predictable responses so you can study the application without an external model.

**Read:** `infrastructure/openai/StubModel.java`, `application-local.yml`.

**Do:** Start the app:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

In another terminal run `examples/local-demo.ps1`, or use `examples/requests.http` in an HTTP client. Deny the credit on your first attempt.

**Observe:** A session ID, a run ID, `WAITING_APPROVAL`, the exact proposed amount, and the result after denial.

**Done when:** You can explain why this mode needs no OpenAI key and why it is not evidence of a real model's intelligence.

### Session 3: Inspect a simple successful request

**Learn:** Conversation/session, execution/run, and business order are different records.

**Read:** `RunRequest`, `RunStore.createSession`, `RunStore.begin`.

**Do:** Create a session, then submit:

```json
{"message":"Find ORD-1001","mode":"AGENT"}
```

Supply an `Idempotency-Key`, for example `lesson-three-001`.

**Exercise:** Change the message but reuse the same key. Predict why the application rejects the request.

**Done when:** You can tell a session ID, run ID, order ID, and idempotency key apart.

## Stage 1 — Understand the model boundary

### Session 4: Model, prompt, token, and context

**Learn:** A prompt is the input sent to a model. Tokens are pieces used to represent input/output; they are not exactly words. A context window limits how much information can fit into one model interaction. Generating a response does not update the model's trained weights.

**Read:** `domain/port/ModelPort.java`, `ContextPolicy.SYSTEM`, `BudgetPolicy.java`.

**Exercise:** On paper, list the input for one call: application instructions, recent conversation, tool schemas, tool results, and optional image. Label which parts are trusted policy and which are untrusted data.

**Done when:** You can explain why a long conversation can cost more even if the latest question is short.

### Session 5: Make your first real SDK call

**Learn:** The SDK handles provider HTTP details. Your code still decides what to send, how much to spend, and what to do with the response.

**Read:** `config/RuntimeConfiguration.java`, `infrastructure/openai/OpenAiModel.java`, especially `parameters()`.

**Do:** Follow “Switch to the real OpenAI model” in the README. Set the key only in your environment. Start with one order-status question, not a long agent run.

**No key yet?** Run this offline test instead:

```powershell
.\mvnw.cmd "-Dtest=OpenAiModelTest" test
```

**Exercise:** Locate `model`, `instructions`, `inputOfResponse`, `store(false)`, and `maxOutputTokens`. Explain each without reading its name aloud as the explanation.

**Done when:** You can identify the one class that depends on the OpenAI request/response models.

### Session 6: Multi-turn conversation and memory

**Learn:** A stateless API does not automatically remember previous requests. This app stores selected conversation items and sends them again. That is application memory, not model training.

**Read:** `RunState.transcript`, `RunStore.begin`, `RunStore.complete`, `ContextPolicy.trimCompletedTurns`.

**Do:** Ask a follow-up in the same session, using a new idempotency key. Compare it with a request in a fresh session.

**Exercise:** Explain why deleting the middle of a tool call/result pair would break the conversation protocol. Find the test that protects the pair.

**Done when:** You can explain why successful history belongs to the session while an unfinished approval lives in the run checkpoint.

### Session 7: Structured output is a contract

**Learn:** “Please return JSON” is a prompt request. A provider JSON schema constrains shape. Application validation separately checks whether returned values are acceptable.

**Read:** `OutputPolicy.schema()`, `OutputPolicy.validate()`, `AnswerResponse`.

**Do:** Run `ContractTest`. Read the invented-evidence and invented-credit cases.

**Exercise:** Add a test proving that an unknown evidence ID is rejected even when the JSON is perfectly formatted.

**Done when:** You can explain why valid JSON can still be a wrong answer. Free-text truthfulness still needs evaluation; the validator cannot prove every sentence is true.

### Session 8: Streaming and image inputs

**Learn:** Streaming delivers incremental events. A fragment is not a complete message. A half-written tool argument must never be executed. Images consume context/cost too.

**Read:** `OpenAiModel.generate`, `AgentController.stream`, `ContextPolicy.validateImage`.

**Do:** Use the streaming request in `examples/requests.http`. Observe `run`, `delta`, `tool`, and `final` events. Local mode simulates text fragments; real mode forwards SDK text deltas.

**Image exercise:** Use a small non-sensitive PNG/JPEG. In PowerShell:

```powershell
$bytes = [IO.File]::ReadAllBytes((Resolve-Path ".\sample.png"))
$image = "data:image/png;base64," + [Convert]::ToBase64String($bytes)
$body = @{message="Describe the visible receipt; do not invent unreadable text"; mode="AGENT"; imageDataUrl=$image} | ConvertTo-Json
```

Use `$body` for a run request with a new key. The image must be at most 256 KB; real vision requires `AI_PROVIDER=SPRING_AI` or `AI_PROVIDER=OPENAI`.

**Done when:** You can explain why remote image URLs are refused and why `final` status matters even if text has already appeared.

## Stage 2 — Tools and agents

### Session 9: A tool call is a proposal

**Learn:** The model returns a tool name and arguments. Your Java code validates them and decides whether to execute. The model does not directly own your database connection.

**Read:** `tools/ToolCatalog.java`, `ToolExecutor.executeRead`, `OpenAiModel.map`.

**Do:** Trace `lookup_order` from schema to SQL query to tool result.

**Exercise:** Submit an unknown tool name, an extra `tenant` property, and an order ID containing SQL text through a unit test. Observe rejection before SQL execution.

**Done when:** You can explain the difference between provider schema enforcement and server-side argument validation.

### Session 10: Read the agent loop

**Learn:** An agent loop repeats “call model → inspect outcome → possibly call tool → return result”. It needs a reason to stop.

**Read:** `AgentServiceImpl.execute`. Focus on the switch over `ModelPort.Outcome`; ignore metrics initially.

**Do:** Draw the five possible outcomes: complete, tools, refusal, incomplete, failure. Identify the Java code for each.

**Exercise:** Temporarily lower `agent.max-steps` to 1 in the local profile. Ask for a credit. Explain why the application stops rather than continuing indefinitely. Restore the setting afterward.

**Done when:** You can name at least four stopping mechanisms: step limit, elapsed-time deadline, budget, and cancellation/human decision.

### Session 11: Deterministic workflow versus agent

**Learn:** Some tasks have a known sequence that Java should control. Others benefit from letting the model select among allowed steps.

**Read:** The `ORDER_STATUS` branch in `AgentServiceImpl.execute`.

**Do:** Submit the same order question once with `AGENT` and once with `ORDER_STATUS` plus `orderId`.

**Exercise:** Decide which architecture fits password reset, free-form support triage, and order-status lookup. Explain your choice using predictability and required permissions.

**Done when:** You can explain that the deterministic workflow still uses a model for wording, while Java selects the business lookup.

### Session 12: Human approval

**Learn:** A prompt saying “ask permission” is not an authorization mechanism. Approval must exist as application state and be checked by code.

**Read:** `RunStore.waitForApproval`, `RunStore.decide`, `SecurityConfiguration`.

**Do:** Request a credit. Inspect the pending amount/order. Stop and restart the app before deciding, then approve as `reviewer`.

**Exercise:** Attempt approval as `developer`. Attempt to put a different amount in the decision body. Explain why both fail.

**Done when:** You can explain four-eyes approval, expiry, exact-argument binding, and why “I approve” inside a chat message is insufficient.

### Session 13: Idempotency and transactions

**Learn:** Networks retry requests. A repeated request must not become a repeated business effect. Database transactions keep related changes together; unique constraints protect invariants even under concurrency.

**Read:** `V1__durable_agent_state.sql`, `RunStore.begin`, `RunStore.decide`, `RunLifecycleTest`.

**Do:** Repeat the same request, then the same approval. Read the corresponding tests.

**Exercise:** Explain these separately: HTTP idempotency key, provider network retry, one-credit-per-order constraint, and atomic approval/ledger transaction.

**Done when:** You can explain why this is not a general “exactly once” guarantee for an external payment provider.

### Session 14: MCP from zero

**Learn:** MCP is a protocol for connecting an AI application to tools and context. A client discovers capabilities from a server. An MCP server is not an LLM.

**Read:** `infrastructure/mcp/PolicyServer.java`, `infrastructure/mcp/McpPolicyGateway.java`.

**Do:** Follow “Turn on MCP” in the README. Compare the embedded policy path with the MCP path. Run `McpProcessIT` through `verify`.

**Exercise:** Identify protocol initialization, `tools/list`, `tools/call`, and `resources/read`. Explain why a resource and a tool are different.

**Done when:** You can explain which process has the OpenAI key, why the child does not inherit it, and why MCP metadata is not an authorization policy.

## Stage 3 — Reliability and security

### Session 15: Prompt injection and trust boundaries

**Learn:** A document, image, user input, or tool result can contain text that looks like a system instruction. The application must not let that text grant permissions.

**Read:** `ContextPolicy.SYSTEM`, `ToolCatalog.validate`, `Actor.from`, `RunStore.visible`.

**Do:** Read the synthetic adversarial cases in `src/test/resources/eval/cases.jsonl`.

**Exercise:** Try an instruction claiming to be an administrator and requesting access to `ORD-9001`. Check the application result and prove no credit was recorded.

**Done when:** You can explain that XML tags help delineate data but are not a security boundary. Tenant checks, allowlisted tools, validated arguments, and approval transactions enforce the boundaries.

### Session 16: Retry, timeout, and ambiguous failure

**Learn:** Some provider failures are transient. Others will not improve with retries. Retrying a partially consumed stream can produce confusing or duplicated behavior.

**Read:** `OpenAiModel.generate`, `retryable`, `waitBeforeRetry`, and `OpenAiModelTest`.

**Do:** Run tests for 429, 401, and an interrupted stream.

**Exercise:** Explain why a 429 can retry before any stream event, a 401 fails immediately, and an interrupted stream fails without replay. Find where the number of attempts is bounded.

**Done when:** You can distinguish “request failed before a result was known” from “the business effect definitely did not happen”.

### Session 17: Context and cost budgets

**Learn:** Input, cached input, and output are different accounting categories. A budget can reserve a conservative amount before a call, then record observed usage afterward.

**Read:** `BudgetPolicy`, `ContextPolicy.inputUpperBound`, the `agent` settings in `application.yml`.

**Exercise:** Compute an estimate for 1,000 input tokens, 500 of them cached, and 100 output tokens using the sample rate card. The expected estimate is USD 0.00041. Explain why cached input is not added on top of the full input count.

**Done when:** You can explain why UTF-8 byte counting is a conservative heuristic, why failed attempts may have unknown usage, and why the provider bill is authoritative.

### Session 18: Tests versus model evaluations

**Learn:** Unit/integration tests prove deterministic contracts. Live evaluations measure a particular model/prompt on particular cases. Passing tests does not prove a model never hallucinates.

**Read:** `ContractTest`, `RunLifecycleTest`, `OpenAiModelTest`, `LiveEvalIT`.

**Do:** Run the default suite. Only after that, optionally run paid live evaluations using the README commands.

**Exercise:** Add a case for a missing order, a misleading image, and a tool result containing hostile instructions. For each, define observable pass conditions without requiring exact wording.

**Done when:** You can distinguish fake-provider flow tests, SDK wire tests, database tests, and real model-quality checks.

### Session 19: Logs, metrics, and debugging

**Learn:** You need evidence about failures without logging prompts, images, API keys, or complete provider exceptions.

**Read:** `RequestBoundaryFilter`, `GlobalExceptionHandler`, metric calls in `AgentServiceImpl`, `audit_events` schema.

**Exercise:** Trigger a missing order, a busy session, and a failed run. Follow the correlation ID and run ID. Identify which data is available from the API and which should stay out of logs.

**Done when:** You can diagnose a failure using category/status and IDs without asking someone to share their secret key.

### Session 20: Deployment and recovery

**Learn:** A production deployment adds verified identity, database operations, networking, capacity limits, monitoring, and recovery. A Dockerfile alone does not establish production readiness.

**Read:** `docs/OPERATIONS.md`, `Dockerfile`, `compose.yml`, `.github/workflows/verify.yml`.

**Do:** With Docker available, run the PostgreSQL gate: `.\mvnw.cmd verify "-DpostgresIT=true"`.

**Exercise:** Explain a crash before approval, a crash after ledger commit, an expired approval, and a cancelled stream. State where the reliable result can be found in each case.

**Done when:** You can explain the remaining infrastructure responsibilities without claiming they are already implemented by a prompt or SDK.

## Stage 4 — Spring AI and Claude-specific study

### Session 21: Spring AI's `ChatClient`

**Learn:** Spring AI offers a consistent Spring API above a provider implementation. This can reduce provider-specific code, but does not remove business/security responsibilities.

**Read:** `docs/SPRING_AI_BRIDGE.md`, especially the concept mapping and first `ChatClient` exercise.

**Exercise:** Open `infrastructure/openai/SpringAiModel.java` and its wire tests. Trace `chatClient.prompt(...).stream().chatResponse()`. Explain the system/user/tool messages, strict schema, usage counters, and why tool auto-execution is disabled. Compare with `OpenAiModel.parameters()`.

**Done when:** You can explain the difference between `OpenAIClient` and Spring AI `ChatClient` and trace how this project's default Spring AI adapter reaches the official OpenAI SDK.

### Session 22: Spring AI memory, advisors, and tools

**Learn:** Advisors can add behavior around model calls. Chat memory manages selected history. `@Tool` can expose typed Java methods to a model.

**Exercise:** Follow the second and third bridge exercises. Wrap only a read-only policy lookup first. Keep the credit write behind `RunStore.decide`; do not expose it as an automatically executable tool.

**Done when:** You can explain why an advisor is not automatically equivalent to a mandatory authorization check, and why conversation IDs require tenant/owner isolation.

### Session 23: RAG, embeddings, and pgvector

**Learn:** An embedding represents text numerically. A vector store retrieves similar content. RAG retrieves relevant documents and includes them in the model input. It does not retrain the model.

**Exercise:** Plan an ingestion flow for three synthetic service-policy documents: split text, attach document IDs and tenant metadata, create embeddings, store vectors, retrieve top matches, and require evidence references. Then implement the optional bridge exercise.

**Done when:** You can explain embeddings versus chat models, memory versus retrieval, and why a vector similarity score is not factual proof.

### Session 24: Prepare for the Claude-specific parts

**Learn:** The transferable architecture does not teach every Claude API or Claude Code behavior. API status names and SDK methods are provider-specific.

**Read:** `docs/CERTIFICATION_COVERAGE.md` and its official sources.

**Exercise:** Compare Claude Messages `stop_reason` with this project's Responses outcome mapping. Study Claude Code context files, permissions, hooks, Agent SDK, and model/caching behavior in the official tools. Explain why an application Java hook is not a Claude Code hook configuration.

**Done when:** You can state precisely what you have practiced with OpenAI, what you have learned in Spring AI, and what still requires Claude-specific hands-on work.

## Completion checklist

- [ ] I can run the local app and describe its limitations.
- [ ] I can trace one complete request from controller to provider and back.
- [ ] I can explain a tool call/result pair and why arguments need validation.
- [ ] I can explain persistence, idempotency, approval, and tenant isolation separately.
- [ ] I can show a failing security or output-contract test and explain the protection it proves.
- [ ] I can distinguish a model-quality evaluation from an infrastructure test.
- [ ] I can account for input, cached input, output, and reserved usage.
- [ ] I can explain what Spring AI adds and what it does not enforce for me.
- [ ] I can describe RAG/pgvector accurately without claiming this app already implements them.
- [ ] I can describe my own changes honestly in an interview.

For your first session, do only sessions 1 and 2. Get the app running, request one order, and deny one proposed credit. The rest can wait until those actions make sense.

## Reading the MVC flow after the package refactor

1. Open `controller/SessionController.java` and find `create()`.
2. Follow its `SessionUseCase` interface into `application/service/impl/SessionServiceImpl.java`.
3. Follow the call to `infrastructure/persistence/RunStore.java` to see the database insert.
4. Inspect `dto/response/SessionResponse.java`: its `id` becomes the HTTP JSON response.
5. Open `controller/AgentController.java`; follow a `RunRequest` into `AgentService` and `AgentServiceImpl`.
6. Compare `dto/request` validation annotations with the response records in `dto/response`.
7. Inspect `exception/GlobalExceptionHandler.java` to see how failures become consistent HTTP problem responses.

Controllers handle HTTP details. Service interfaces declare application operations. Implementations coordinate those operations. The repository performs database work. Constructor injection connects these components without controllers creating them manually.
