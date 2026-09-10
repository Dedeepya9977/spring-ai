# Agent Foundations — Automotive Support

A Spring Boot/Maven backend for learning how an AI application calls a model, uses tools, preserves conversation state, and pauses for a human decision. All automotive records and policies are synthetic.

**Start with [LEARNING_PATH.md](LEARNING_PATH.md).** You do not need an API key, Docker, PostgreSQL, or any AI knowledge for the first lessons.

The main application uses the **official OpenAI Java SDK**, not Spring AI. Shared concepts and Spring AI-specific APIs are explained separately in [SPRING_AI_BRIDGE.md](docs/SPRING_AI_BRIDGE.md). This is a production-oriented reference implementation. Deployment readiness still depends on your identity provider, infrastructure, live model evaluations, load testing, and operational review; see [OPERATIONS.md](docs/OPERATIONS.md).

## What you can do

- Ask about a synthetic service order using a bounded tool-calling agent.
- Run an explicit order-status workflow where Java selects the lookup.
- Submit text or a small base64 PNG/JPEG and stream provider output through SSE.
- Request a service credit; inspect the exact proposed arguments before a different person approves.
- Resume a persisted approval after restarting the application.
- Replay an HTTP request using the same idempotency key without repeating the workflow.
- Connect a real local MCP client/server pair for policy tools and resources.
- Run adversarial and failure-path tests without paying for model calls.

The credit is a **local database ledger entry**, not a payment-gateway refund. `RunView.credits` remains authoritative even if the model's explanation fails after approval.

## Versions

| Component | Pinned version / requirement |
| --- | --- |
| Java source/bytecode | 17; JDK 21 is also suitable and used in Docker/CI |
| Spring Boot | 3.5.16 |
| Official OpenAI Java SDK | 4.61.0 |
| MCP Java SDK | 2.0.1 with Jackson 2 integration |
| Maven wrapper | Maven 3.9.11 |
| Production database | PostgreSQL; compose example uses 17 |
| Local learning database | H2 in PostgreSQL compatibility mode, persisted under `data/` |

Versions were resolved from Maven Central on 2026-09-10. Boot 3.5 is deliberately used to keep Jackson 2 and the familiar Spring MVC stack together. Revalidate dependencies before deploying later.

## First run — Windows / IntelliJ

1. Install a JDK (17 or 21). Open **this folder's `pom.xml`** as a Maven project in IntelliJ.
2. Use the project JDK for both the project and Maven runner. Let IntelliJ import dependencies.
3. In PowerShell, from this project folder:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

4. Leave the application running. In a second PowerShell terminal:

```powershell
.\examples\local-demo.ps1
```

If script execution is restricted, copy the commands from the script into your terminal; you do not need to change your machine's execution policy. `examples/requests.http` also works in an HTTP client that supports this format. Postman can use the same endpoints and JSON bodies.

## First run — Linux / macOS / WSL

```bash
./mvnw verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Then, in another terminal:

```bash
python3 examples/local_demo.py
```

The first Maven build downloads dependencies. Later offline builds can use `./mvnw -o verify` when all dependencies are cached. Default tests need no API key and invoke no external model.

### Local identities and data

| Identity / record | Purpose |
| --- | --- |
| `developer` / `local-only` | Creates sessions, requests runs, cancels own runs |
| `reviewer` / `local-only` | Reviews same-tenant runs and makes approval decisions |
| `ORD-1001` | Demo tenant, delayed, INR 2,500 order value |
| `ORD-1002` | Demo tenant, delivered, ineligible for a delayed-service credit |
| `ORD-9001` | Another tenant; inaccessible to demo users |

These credentials are enabled only by the explicit `local` or `test` profile. The local server binds to `127.0.0.1`. The `STUB` provider is a simple scripted test double, **not an AI model**; its wording and behavior are intentionally limited.

## Switch to the real OpenAI model

Set the key in your terminal/environment or IDE secret settings. Do not put it in source files or paste it into chat. Your API account needs access to the configured model and API billing.

```powershell
$env:OPENAI_API_KEY = "your-api-key"
$env:AI_PROVIDER = "OPENAI"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

```bash
export OPENAI_API_KEY='your-api-key'
export AI_PROVIDER=OPENAI
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`OPENAI_MODEL` and `OPENAI_ECONOMY_MODEL` default to `gpt-4.1-mini`. They are kept equal until you have evaluation evidence for a different routing choice. The application sends full selected history on each call with provider storage disabled. `store=false` is **not** a promise of zero provider retention; review the provider's data controls for your account.

Adjust the three rate-card environment variables when changing a model. For two different models, configure the highest applicable rates as conservative shared bounds, or extend the rate card to a map keyed by model. Reported cost is an estimate; unknown usage from failed attempts remains reserved rather than being treated as free.

## Turn on MCP

Package first; the application starts the same jar in a separate, restricted-environment process:

```bash
./mvnw package
java -jar target/agent-foundations-1.0.0.jar --spring.profiles.active=local --agent.mcp.enabled=true
```

Use the same `java` command in PowerShell. Run it from this project folder, or set an absolute `MCP_SERVER_JAR`. MCP startup performs protocol negotiation and tool discovery. The server exposes `search_policy` and `policy://service-credit/v1`; it has no arbitrary SQL, shell, file, or network tool. The app keeps order authorization and all writes inside Java.

## HTTP API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/sessions` | Create an owned conversation |
| POST | `/api/sessions/{id}/runs` | Execute a run; `Idempotency-Key` required |
| POST | `/api/sessions/{id}/runs/stream` | Same flow with SSE events |
| GET | `/api/runs/{id}` | Read result, pending proposal, token usage, and ledger receipts |
| POST | `/api/runs/{id}/decision` | Approver-only accept/deny and resume |
| POST | `/api/runs/{id}/cancel` | Stop an owned run |
| DELETE | `/api/sessions/{id}` | Delete inactive conversation/run data |

Input example:

```json
{"message":"Please request an INR 100 credit for ORD-1001","mode":"AGENT"}
```

Code-selected workflow:

```json
{"message":"Explain the status of this order","mode":"ORDER_STATUS","orderId":"ORD-1001"}
```

Approval body contains only the decision and review reason. Amount/order/tool arguments come from the immutable persisted proposal:

```json
{"approve":true,"reason":"Reviewed the order delay and proposed amount"}
```

For SSE, `delta` events contain provisional provider JSON fragments. They are not validated answers or executable tool arguments. Wait for `final` and inspect its status. Use `GET /api/runs/{id}` after a disconnect; do not blindly start another run. Application-level failures inside a started run are represented by its terminal status/error code. HTTP-level admission/validation failures use Problem Details.

## Tests

```bash
./mvnw verify
# Additional real PostgreSQL migration/concurrency gate; Docker must be running:
./mvnw verify -DpostgresIT=true
```

Default verification includes SDK HTTP/SSE contract tests against MockWebServer, H2 lifecycle/security tests, pure contract tests, and a real packaged MCP process test. PostgreSQL is a separate required deployment gate; H2 compatibility does not prove PostgreSQL locking behavior.

Opt-in live model evaluations send seven synthetic cases and can incur cost:

```bash
export OPENAI_API_KEY='your-api-key'
export OPENAI_LIVE_EVALS=true
./mvnw verify
```

PowerShell equivalent: set `$env:OPENAI_API_KEY` and `$env:OPENAI_LIVE_EVALS = "true"`, then run `.\mvnw.cmd verify`. Remove `OPENAI_LIVE_EVALS` afterward to avoid accidental paid runs. Results appear in `target/failsafe-reports`; coverage is in `target/site/jacoco/index.html`. A passing scripted test suite is not evidence of live model answer quality.

## Reading map

| Document | Read it when |
| --- | --- |
| [LEARNING_PATH.md](LEARNING_PATH.md) | Starting from zero AI knowledge |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Following a request through the system |
| [CERTIFICATION_COVERAGE.md](docs/CERTIFICATION_COVERAGE.md) | Comparing your syllabus with implemented features and Claude-specific study gaps |
| [SPRING_AI_BRIDGE.md](docs/SPRING_AI_BRIDGE.md) | Learning Spring AI abstractions and later RAG/pgvector work |
| [OPERATIONS.md](docs/OPERATIONS.md) | Configuring security, PostgreSQL, failure recovery, and deployment |
| [VALIDATION.md](docs/VALIDATION.md) | Checking what was actually executed in the build environment |

Official references are linked alongside the relevant explanations in these guides. This project is not an official Anthropic course or an exam-coverage guarantee.
