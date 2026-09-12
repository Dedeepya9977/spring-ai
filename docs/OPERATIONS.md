# Operations and deployment

## Deployment contract

The source implements production-oriented application controls. It has not been deployed into your infrastructure. Before calling your deployment production-ready, pass the PostgreSQL and live-model gates, configure your real identity provider, assess load/capacity, and establish the operational controls below.

Do not enable `local` or `test` in an exposed deployment. Those profiles enable teaching credentials and local fixtures. `STUB` is rejected outside those explicit profiles.

## Required environment

| Variable | Purpose |
| --- | --- |
| `OPENAI_API_KEY` | Provider key, supplied by your secret manager |
| `DB_URL` | PostgreSQL JDBC URL; use TLS for remote database traffic |
| `DB_USERNAME`, `DB_PASSWORD` | Application database credentials |
| `OIDC_ISSUER` | Trusted issuer discovery URL |
| `OIDC_AUDIENCE` | Expected audience, default `agent-foundations` |
| `OPENAI_MODEL`, `OPENAI_ECONOMY_MODEL` | Models approved for this workload |
| `INPUT_USD_PER_MILLION`, `CACHED_INPUT_USD_PER_MILLION`, `OUTPUT_USD_PER_MILLION` | Conservative rates for configured models |
| `MCP_ENABLED` | Enables the local policy MCP process; default false |
| `MCP_SERVER_JAR`, `MCP_JAVA_COMMAND` | Operator-controlled executable configuration |

`AI_PROVIDER` defaults to `SPRING_AI` outside the local profile. `OPENAI` selects the direct Responses adapter. Create fresh sessions when switching adapters, and evaluate both modes separately. Missing database, identity, or API credentials cause startup failure. Do not “fix” that by disabling security in production.

## JWT contract

Use signed JWT access tokens from the configured issuer. Spring Security validates the signature using the issuer's published keys; this application adds expected audience validation and requires tenant/subject claims when creating the actor.

Required claims:

```json
{
  "iss": "https://your-identity-provider.example/issuer",
  "aud": ["agent-foundations"],
  "sub": "unique-user-id",
  "tenant_id": "organization-id",
  "scope": "agent:use"
}
```

This is a claim-shape example, not a usable token. Your identity provider must manage trusted tenant membership and approved scopes. Never mint a token from a caller-supplied tenant value without verifying membership.

| Scope | Access |
| --- | --- |
| `agent:use` | Create/use owned sessions and cancel owned runs |
| `agent:approve` | Read same-tenant runs and submit approval decisions |
| `agent:metrics` | Read Prometheus metrics |

The same person cannot approve their own run even if they have both use and approval scopes. Administrative revocation or role changes can require additional live authorization checks if your identity provider issues long-lived tokens; choose token lifetimes and revocation strategy for your organization.

## Build and database

```bash
./mvnw -B verify -DpostgresIT=true
docker build -t agent-foundations:1.0.0 .
```

The PostgreSQL test requires Docker. The image build skips tests only after CI has passed the verification job. The example runtime image uses a non-root account. Pin reviewed base-image/action digests in your release process and scan application dependencies and the image before deployment.

`compose.yml` starts a loopback-bound PostgreSQL instance for infrastructure practice:

```bash
export DB_PASSWORD='set-a-local-development-password'
docker compose up -d db
```

The default app profile uses only `db/migration`; it does not insert local synthetic orders. Local/test profiles add the repeatable fixture migration from `db/local`. Supply your actual authorized order adapter/data ingestion before deploying for a real business workflow.

Flyway currently runs with the application datasource. For strict production separation, run migrations as a deployment job with DDL privileges, disable application Flyway, and grant the runtime account only required DML access. Test both clean installation and upgrade paths on your actual PostgreSQL version.

## Network and resource controls

- Terminate TLS at a trusted ingress and restrict application/database connectivity. Do not expose the teaching Basic-auth profile.
- Apply authenticated user/tenant request quotas and daily spend limits at ingress or a shared service. Current semaphores bound concurrency per process only; they do not provide global tenant quotas or prevent unlimited session creation over time.
- Set ingress body limits at or below 400 KB, plus request/idle/write timeouts appropriate for a 120-second run. The application also bounds JSON bodies, including chunked requests.
- For SSE, disable proxy buffering, bound concurrent connections, and choose write/idle timeouts. The synchronous emitter can be held by a slow client until container/ingress write handling ends the connection; test backpressure and disconnect behavior under load.
- Use egress restrictions for provider, identity and database endpoints. Remote image URLs and arbitrary model-selected URLs are not supported.
- Set CPU/memory limits and a writable temporary directory for the JVM. If MCP is enabled, account for the extra JVM/process. Environment scrubbing does not replace an OS/container sandbox.

## Data protection and retention

Prompts, selected images and tool outputs are stored in the database as run/session context. Use synthetic data during learning. Production deployment needs appropriate encryption at rest, backups, access restrictions, retention and provider data-handling decisions. `store=false` disables Responses storage behavior; it does not establish every provider retention control.

The hourly cleanup deletes inactive conversation/session/run data older than `agent.retention` (default seven days), using `updated_at`. Deleting an inactive session also deletes its runs/approvals. Audit metadata and the business credit ledger are deliberately retained separately; set a business/legal retention policy and a separate approved cleanup mechanism for those tables.

Idempotency history lives with runs. After a conversation is deleted, its run keys are no longer available. The ledger's unique order constraint independently prevents a second local credit for the same tenant/order.

Logs contain run/correlation IDs and failure categories, not prompt bodies, images, tool arguments, secrets or full provider exceptions. Before integrating a new SDK/logger, verify its error and debug logging behavior. Do not enable verbose provider HTTP logging with real customer data.

## Recovery table

| Incident | Current behavior | Operator/client action |
| --- | --- | --- |
| HTTP request retried | Same session/key/body returns the same run resource | Reuse the original key; inspect current status |
| Provider 429/temporary failure before events | Up to one bounded retry, when delay fits | Inspect terminal status if retry fails |
| Provider stream interrupted | Run fails; no automatic replay | GET the run; start a deliberately new request only after inspection |
| Process crashes before completion | PROCESSING row eventually expires; session is released | Inspect error and any ledger receipts |
| Process restarts while awaiting approval | Pending proposal remains persisted | Reviewer can decide before its expiry |
| Approval expires | Recovery marks the run EXPIRED and cancels pending approval | Submit a new reviewed request if still needed |
| Order changes before approval | Transaction rejects approval after fresh policy checks | Review the new facts; deny/cancel obsolete proposal |
| Ledger commit succeeds; model explanation fails | Credit remains committed and appears in `credits` | Treat ledger receipt as authoritative; do not infer rollback from FAILED |
| Duplicate approval | Conflict; no second ledger effect | Read the existing run/receipt |
| Owner cancels | No further accepted checkpoints; active session released | Read status; in-flight calls may take until timeout to unwind |
| Database unavailable | Request fails; no bypass or in-memory fallback | Restore database service; inspect ambiguous outcomes by run/ledger IDs |

No external effect is automatically retried. When adding a payment/ticket/email provider, introduce a durable outbox, recipient/action authorization as applicable, stable provider idempotency keys and reconciliation. A local database transaction cannot roll back an already delivered external request.

## Observability

Health probes are available under `/actuator/health`. Prometheus access requires the metrics scope in the production profile. Export run duration, input/output token counts and bounded tool-name/success tags. Avoid per-user/run IDs as metric labels because that creates unbounded cardinality.

Add deployment alerts for increasing failed/expired runs, rejected approvals, provider throttling, database errors, saturated concurrency, and unusual token/spend consumption. Validate alerts and recovery playbooks in staging; a metric existing in code is not an operational alert.

## Release gates that remain environment-specific

1. Real PostgreSQL migrations and concurrent-approval tests pass.
2. OIDC signature/key rotation, issuer, audience, expiry, tenant membership and scope policies work with your identity provider.
3. Paid live evaluations pass against the exact model configuration; investigate failures rather than widening acceptable outcomes blindly.
4. Review streaming display behavior, hallucination risk and prompt-injection cases for your actual documents/data.
5. Load tests establish capacity and timeouts for your database/provider limits; global tenant quotas are configured.
6. Dependency/image scan, secret management, TLS, network policy, backups and restore drill are complete.
7. Business owners approve the actual credit/refund policy and retention behavior before any real financial integration.

The included ledger is a synthetic engineering example, not financial advice or an approved production refund policy.
