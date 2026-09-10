# From the OpenAI SDK project to Spring AI

## What changes when you use Spring AI?

This application injects `OpenAIClient` and constructs provider-specific Responses requests. Spring AI provides Spring-facing abstractions such as `ChatClient`, `ChatModel`, prompt/message objects, memory, tools, and retrieval components. Your controllers, transactions, tenant policy, approval state, and business rules still belong to your application.

Learn the direct SDK flow first. Then create a separate learning branch or small project for Spring AI so dependency choices and API differences remain visible. Do not add an arbitrary current Spring AI starter to this project's POM without checking its Spring Boot compatibility. This main project pins Boot 3.5; current documentation may describe a newer Spring AI/Boot generation. [Spring AI getting started](https://docs.spring.io/spring-ai/reference/getting-started.html).

At the documentation check on 2026-09-10, Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x. Use that compatible generation for a new Spring AI lab, or consult versioned documentation before extending this Boot 3.5 project. The two POMs should not be mixed casually.

## Concept map

| In this project | Spring AI concept to learn | What you still must enforce |
| --- | --- | --- |
| `OpenAIClient` / `OpenAiModel` | `ChatModel` and `ChatClient` | Provider options, allowed models, timeout/usage policy |
| `ContextPolicy.SYSTEM` | System prompt / `PromptTemplate` | Trust boundaries; safe interpolation |
| `RunState.transcript` | `ChatMemory`, memory repository and memory advisor | Tenant/owner isolation and complete tool exchanges |
| `OutputPolicy` | Structured output/entity conversion | Semantic/business validation and evidence checks |
| `ToolCatalog` | `@Tool`, descriptions, tool callbacks | Exact arguments, identity, least privilege, authorization |
| `AgentService` checks | Advisors and application services | Mandatory controls must not be bypassable by the model |
| `PolicyGateway` | Tool callback or MCP integration | Server trust, tool allowlist, result bounds |
| SDK event stream | `ChatClient.stream()` | Cancellation, terminal state, provisional content handling |
| `BudgetPolicy` and Micrometer | Response usage metadata and observations | Real budgets, rate card, per-tenant limits |
| Fixed policy retrieval | Later `VectorStore` + retrieval advisor | Ingestion quality, ACL filtering, evidence, evaluation |

## Lab A — One `ChatClient` endpoint

**Goal:** Learn the fluent API without involving agents or database writes.

1. Create a small Spring Boot project using a supported Spring AI BOM/starter combination from the official getting-started page. Use the starter for your chosen provider. Keep versions pinned.
2. Configure the API key through the provider's environment/property mechanism. Keep it out of source control.
3. Inject an auto-configured `ChatClient.Builder` and create a read-only service:

```java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class LearningChatService {
    private final ChatClient chat;

    LearningChatService(ChatClient.Builder builder) {
        this.chat = builder.defaultSystem(
                "Explain one backend concept in three short sentences.").build();
    }

    String explain(String concept) {
        return chat.prompt().user(concept).call().content();
    }
}
```

This is a learning snippet, not an additional compiled module in the supplied project. Add authentication, input bounds, timeout and usage limits before exposing an endpoint publicly. The fluent API supports synchronous and streaming interactions; study response metadata rather than retaining only the output string. [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html).

**Compare:** Identify the equivalent of `instructions`, user input, configured model, and returned content in `OpenAiModel.parameters()`.

**Success condition:** You can switch an explanation request between two configurations without moving business logic into a prompt.

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

After finishing the main project: “Built an OpenAI SDK integration in Spring Boot with tool calling, durable approvals, conversation state, MCP and contract tests,” provided you understand and have exercised those features.

After completing the additional labs: name the Spring AI APIs and RAG work you actually implemented and evaluated. Reading this bridge alone is not hands-on implementation experience.
