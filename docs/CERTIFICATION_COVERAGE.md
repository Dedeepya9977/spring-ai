# Certification coverage and corrections

Checked against accessible documentation on **2026-09-10**.

## What could be verified

The official exam-delivery provider lists **Claude Certified Developer – Foundations (CCDV-F)** as a certification. The Anthropic academy/partner pages were inaccessible from this build environment, so this project does **not** claim to verify the complete official blueprint, domain numbering, subskill weights, or the “about 33%” figure. Obtain your exam's current guide from the academy and use it as the authority. [Pearson VUE's Anthropic certification page](https://www.pearsonvue.com/us/en/anthropic.html).

Your outline is a useful engineering checklist. Its numbering skips 4, and it should include explicit study of Claude Code and the Claude Agent SDK, identity/secrets, configuration and deployment lifecycle, model fundamentals, and retrieval/evaluation design. Those are relevant product and engineering topics; this document does not invent exam weights for them.

One concrete API correction: Claude's documented stop reasons also include `pause_turn` and `model_context_window_exceeded`. The existing list should not be treated as exhaustive. Streaming events also need to be distinguished from application conversation updates. [Claude stop reasons](https://platform.claude.com/docs/en/build-with-claude/handling-stop-reasons).

## Implementation map

“Implemented” means there is an application execution path or test in this repository. “Study/extension” means documentation or an exercise, not a shipped runtime feature.

| Topic from your outline | Status | Location and scope |
| --- | --- | --- |
| API/SDK integration | Implemented for OpenAI | `SpringAiModel` uses Spring AI and official SDK Chat Completions; `OpenAiModel` supports direct Responses |
| Stateless and multi-turn conversation | Implemented | `RunStore`, `RunState`, explicit history replay and ownership |
| Content boundaries | Implemented | `ContextPolicy`, distinct message roles/tool outputs; XML escaping in deterministic flow |
| Stop reasons/status handling | Implemented for OpenAI; Claude study | Response status + content type mapping; Claude differences below |
| Spring AI integration | Implemented | `ChatClient`, `OpenAiChatModel`, messages, tool definitions, strict schema, stream usage; auto-execution advisor disabled |
| Real provider streaming | Implemented | SDK SSE consumption and `/runs/stream`; terminal-event requirement |
| Image + text input | Implemented | Bounded PNG/JPEG data URLs, low-detail option in the direct Responses adapter; no remote URL fetch |
| Audio/video/PDF input | Extension | Not implemented; “multimodal” does not imply all media types |
| Idempotency and integration failures | Implemented | Scoped keys, conditional state updates, limited transient retries |
| Deterministic vs agentic design | Implemented | `ORDER_STATUS` vs `AGENT` modes |
| Bounded agent loop | Implemented | Steps, time, context, token/cost limits, cancellation and recovery |
| Human-in-the-loop | Implemented | Durable approval, expiry, different reviewer, atomic ledger write |
| Typed tool descriptions/schema | Implemented | `ToolCatalog`, strict provider schemas |
| Model argument validation | Implemented | Duplicate/extra field rejection, exact types, money bounds, tenant-scoped lookup |
| MCP client/server | Implemented | Stdio process; initialize, discover, call tool, read resource |
| Remote MCP authentication / HTTP transport | Study/extension | Local stdio is implemented; remote OAuth/transport controls need a separate design |
| Structured tool errors | Implemented | `Reply` with category, retryability, feedback; MCP `isError` on server errors |
| Prompt engineering | Implemented + exercises | Stable policy, untrusted inputs, explicit tool descriptions, controlled final shape |
| Context/token hygiene | Implemented | Whole-turn trimming, bounded images/results, conservative reservations |
| Deterministic hooks | Implemented in Java; Claude study | Mandatory control points in `AgentService` and `RunStore` |
| Identity, secrets, access control | Implemented + deployment config | JWT issuer/audience validation, scopes, tenant/owner checks, env secrets |
| Prompt injection/data leakage controls | Implemented defense layers | No arbitrary execution tool, bounded inputs, isolated identity, protected writes; no universal safety guarantee |
| Unit/integration/adversarial tests | Implemented | Contract, SDK wire, security, lifecycle, MCP-process tests |
| Real model quality evaluations | Implemented, opt-in | Seven synthetic cases in `LiveEvalIT`; requires API key and paid calls |
| Model routing | Implemented, conservative default | Code chooses standard/economy configuration; both default to the same model pending evaluations |
| Token/cost accounting | Implemented | Input/cached/output usage, pre-call reservations, configurable rate card |
| Prompt caching optimization | Partial | Stable prefix and cached-token accounting; no application cache guarantee or automatic tuning |
| Claude Code | Study/extension | Context files, tool permissions, CLI workflows, skills/hooks and review practice |
| Claude Agent SDK / multi-agent handoffs | Study/extension | This app implements one bounded agent directly; no fake SDK or multi-agent claim |
| RAG, embeddings, pgvector | Study/extension | Covered in the learning path and Spring AI bridge; not in the main runtime |
| Batch API / fine-tuning | Study/extension | Cost/throughput and adaptation topics; no batch job or training pipeline shipped |

`errorCategory` and `isRetryable` are this application's tool-result fields. They are not claimed to be mandatory MCP-standard fields. MCP also has JSON-RPC/protocol errors, which are distinct from a tool result marked `isError`.

## Direct Responses adapter compared with Claude

The table below applies to `AI_PROVIDER=OPENAI`. The default Spring AI Chat Completions adapter maps `stop`, `tool_calls`, `length`, `content_filter`, and refusal metadata; see `SpringAiModel` and its tests.

| Claude concept | OpenAI implementation in this project | Difference to remember |
| --- | --- | --- |
| Messages API | Responses API | Different request/event schemas and SDK classes |
| `end_turn` | Completed response with final output | Completion status alone does not mean a business action occurred |
| `tool_use` | `function_call` output item | Tool execution requires Java validation and dispatch |
| `tool_result` | `function_call_output` input item | Correlate using the provider's call ID |
| `max_tokens` | Incomplete response due to an output limit | Do not execute a truncated tool call or accept partial JSON |
| `stop_sequence` | No application-level equivalent configured | Do not invent a one-to-one Responses status |
| `refusal` | Refusal content in an output message | A completed response can contain refusal content |
| `pause_turn` | No direct equivalent implemented | Study Claude server-tool continuation semantics separately |
| Context-window exhaustion | Local context guard + provider failure/incomplete handling | Local estimates are not an exact model tokenizer |
| Claude prompt cache controls | Stable prefix and OpenAI cache usage counters | Cache behavior and billing semantics differ by provider |
| Haiku/Sonnet/Opus | Configurable OpenAI model IDs | Not interchangeable labels or guaranteed cost/quality tiers |
| Claude hooks | Mandatory Java checks here | Learn actual Claude Code hook events/configuration separately |

Consult the current [OpenAI Responses documentation](https://developers.openai.com/api/docs/guides/responses) and [Claude Messages documentation](https://platform.claude.com/docs/en/build-with-claude/overview) when implementing another adapter. Do not blindly translate enum strings between them.

## Claude-specific hands-on work to add

1. Use Claude Code on a small disposable branch: inspect context, ask for a change, review the diff, and run tests yourself. Learn context files, permissions, and command behavior from the [official Claude Code overview](https://code.claude.com/docs/en/overview).
2. Study named hook events and their documented blocking semantics, including failure behavior. Compare a CLI hook with `BudgetPolicy.reserve` and `RunStore.decide` in this application. A hook that times out or merely logs is not necessarily a security gate. [Claude Code hooks](https://code.claude.com/docs/en/hooks).
3. Build a small Claude Agent SDK exercise in a supported language and inspect the SDK's sessions, permissions and tool handling. This Java project does not embed that SDK. [Claude Agent SDK overview](https://platform.claude.com/docs/en/agent-sdk/overview).
4. Practice Claude-specific streaming, stop reasons, caching, context handling, and model choices against official documentation, then map the observed behavior to tests.
5. Compare your current official exam guide with this matrix and add any missing objective. A study project's breadth is not a guarantee of exam coverage or passing.
