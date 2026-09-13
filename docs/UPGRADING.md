# Spring Boot 4 and Spring AI migration

Verified on 2026-09-12 against Maven Central metadata and official Spring documentation.

| Component | Previous project | Current project |
| --- | --- | --- |
| Spring Boot parent | 3.5.16 | 4.1.1 |
| Spring AI | Documentation only | 2.0.1 BOM and compiled model/client modules |
| Default real provider | Direct OpenAI Responses | Spring AI Chat Completions over official OpenAI SDK |
| Application JSON | Jackson 2 | Jackson 3 |
| MCP JSON | Jackson 2 | Jackson 3, aligned with the schema validator |
| OpenAI SDK JSON | Jackson 2 | SDK-specific mapper stays on Jackson 2 |
| Web starter | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| Flyway auto-configuration | Implicit via library dependency | `spring-boot-starter-flyway` |
| OAuth resource-server starter | `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| MVC tests | Older test annotation package | `org.springframework.boot.webmvc.test.autoconfigure` |
| Testcontainers | 1.x artifact names | Boot-managed 2.x artifacts and PostgreSQL package |

The Spring project page lists Boot 4.1.1. Maven Central also lists 4.2.0-M1, a milestone preview. This application pins the latest stable release verified at the check above. Spring AI 2.0.x supports Boot 4.0.x and 4.1.x. [Spring Boot project](https://spring.io/projects/spring-boot/), [Spring AI compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html).

Boot 4 changes the module layout, testing packages, and default Jackson generation. Application parsing moved to `tools.jackson` with strict duplicate/unknown-field and size limits retained. The OpenAI SDK's own mapper remains a separate Jackson 2 boundary. MCP uses `mcp-json-jackson3` so its schema validator matches Spring AI's Jackson 3 validator dependency. Mixing the Jackson 2 MCP validator with the Jackson 3 validator artifact caused a `NoSuchMethodError` in the packaged server and was corrected. [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).

## Provider selection

- `SPRING_AI`: the default real integration, using Spring AI `ChatClient` and `OpenAiChatModel`. SDK retries are disabled, and each call gets a bounded timeout. A missing finish reason or usage record fails the run with its reservation retained.
- `OPENAI`: the direct Responses adapter, useful for learning provider-specific events and encrypted reasoning-history round trips. Its bounded transient retry policy is unchanged.
- `STUB`: the scripted local/test mode. No API key or Python runtime is needed.

Create new sessions when switching provider adapters. Existing database migrations and ledger/approval semantics are unchanged; provider history is API-specific.

Spring AI 2's automatic tool-calling advisor is disabled. Tool callbacks expose schemas only, and the application remains the sole authority for validation, reads, and human approvals. Test `partialToolArgumentsAreAssembledAndReturnedWithoutExecutingCallbacks` verifies this at the real Spring AI/SDK HTTP boundary.

## Verify an upgrade

Run `./mvnw verify`, then `./mvnw verify -DpostgresIT=true` with Docker. Opt-in live evaluations default to Spring AI; `LIVE_EVAL_PROVIDER=OPENAI` selects the direct adapter. Run both if deploying both modes. Consult `VALIDATION.md` for checks executed here and the outstanding deployment gates.

The source baseline remains Java 17; Docker and CI use JDK 21. Updating the framework parent does not require adopting a preview JDK. Review release notes and supported stable versions together for future upgrades rather than changing the parent number alone.

## Local database shutdown

The local H2 URL includes `DB_CLOSE_ON_EXIT=FALSE`, allowing Spring to close the connection pool during graceful shutdown. An immediate process restart previously lost pending run records in the packaged smoke check; this setting resolved the observed failure. `PackagedRestartIT` now starts the executable JAR twice and verifies approval persistence, reviewer authorization, a single credit receipt, idempotent replay, and servlet SSE output. The test uses the actual local profile in a temporary working directory. Production continues to use PostgreSQL. [Spring Boot embedded database configuration](https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.datasource.embedded).
