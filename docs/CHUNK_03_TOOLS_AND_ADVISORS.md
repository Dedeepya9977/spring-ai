# Chunk 3 — Spring AI tools and advisors

This chunk adds two deliberately narrow Spring AI integration points:

- PolicyTools exposes a read-only search_policy function using @Tool and @ToolParam.
- ModelTimingAdvisor records model latency without recording prompts, images, keys, or provider payloads.

The existing application-owned agent loop remains authoritative. The SpringAiModel constructor disables Spring AI's automatic tool-execution advisor and installs the timing advisor. Tool definitions supplied to the model still throw if invoked outside the application service, so the model can propose a call without bypassing validation, authorization, or durable approval.

The policy tool is intentionally not wired into the automatic execution path. It is a safe learning boundary for inspecting generated tool metadata and comparing annotation-based callbacks with the existing ToolCatalog.

Validation:

- PolicyToolsTest checks the generated tool name, description, input schema, successful read, and unsupported-topic rejection.
- The existing Spring AI adapter and lifecycle tests continue to cover the provider boundary.
- The repository workflow runs Maven verification and the container image build.
