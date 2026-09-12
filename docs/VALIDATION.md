# Validation record

Build environment: Linux, OpenJDK 17, Maven 3.9.11. Migration validation date: 2026-09-12. Framework versions: Spring Boot 4.1.1, Spring AI 2.0.1, MCP Java SDK 2.0.1 with Jackson 3.

## Executed successfully

- Maven compilation, packaging, unit tests, and integration-test lifecycle through `verify`.
- **60 executed test cases passed**, with zero failures and zero errors.
- Spring AI ChatClient and official SDK wire tests: streaming, strict schema, tool-call assembly without callback execution, refusals, missing finish/usage, media and tool-result history.
- Official OpenAI SDK request serialization and SSE parsing against a local HTTP test server.
- Transient 429 retry, 401 non-retry, interrupted-stream non-replay, refusal and incomplete-output handling.
- Structured output, evidence, money, extra/duplicate-field, context and token/cost contract tests.
- HTTP authentication/approval restrictions and identity-claim validation tests.
- H2-backed lifecycle, idempotency, concurrency, approval, cancellation, expiry and cross-tenant isolation tests.
- Real packaged stdio MCP server process: negotiation, tool discovery/call, resource read and close.
- `PackagedRestartIT`: packaged Spring Boot application over actual HTTP, including approval persistence across process restart, a single ledger receipt, idempotent replay, and servlet SSE output.

## Test breakdown

| Suite | Executed tests |
| --- | ---: |
| `ContractTest` | 20 |
| `OpenAiModelTest` | 8 |
| `SpringAiModelTest` | 11 |
| `RunLifecycleTest` | 12 |
| `ApiSecurityTest` | 5 |
| `JwtPolicyTest` | 2 |
| `McpProcessIT` | 1 |
| `PackagedRestartIT` | 1 |
| **Total executed** | **60** |

The JUnit report also contains two skipped opt-in test containers/classes. These are not counted as passes. Live evaluation cases are generated only when their opt-in condition is enabled.

## Not executed here

- `PostgresIT`: requires a running Docker daemon and `-DpostgresIT=true`. The test is included and required by the supplied CI workflow.
- `LiveEvalIT`: requires `OPENAI_LIVE_EVALS=true`, a real API key and paid provider access. Seven representative/adversarial cases are included. Defaults to Spring AI; use `LIVE_EVAL_PROVIDER=OPENAI` to evaluate the direct adapter separately.
- Docker image build/run, cloud deployment, real identity-provider discovery/key rotation, performance/load testing, dependency/image vulnerability scanning, and Windows execution.

H2 tests are not a substitute for PostgreSQL-specific verification. Mocked HTTP responses are not a live OpenAI evaluation. JWT claim-validator tests do not replace testing your issuer/key infrastructure. PowerShell examples are supplied for learning but were not executed in this Linux environment.

## Reproduce

```bash
./mvnw verify
./mvnw verify -DpostgresIT=true
```

Use `.\mvnw.cmd` in PowerShell. The second command needs Docker. See the README for explicit paid live-evaluation setup.

The repository contains source, test code, configuration, Maven wrapper and documentation. Generated `target/` output, local databases, credentials, and build-environment files are excluded. Build the executable jar from the supplied source.
