# koog-context-orchestrator

KOOG context orchestration library for explicit, node-graph-driven memory management.

This module is designed to be generic across agent types (chat, story, tool-heavy, custom graphs) without implicit coupling to a specific node pipeline.

## What It Does

- Tracks prompt/context pressure per turn.
- Decides when and how to compact memory (light/structured/aggressive/emergency).
- Schedules compaction asynchronously.
- Applies latest compacted artifact only when you explicitly place the apply node.
- Exposes runtime telemetry (`ContextRunTelemetry`) for UI and checkpoint persistence.

## Core Design

The compaction workflow is separated from your main business/LLM node:

1. Context node evaluates budget and may enqueue compaction.
2. Background worker runs a dedicated compactor backend (deterministic or private LLM agent).
3. Result artifact is stored in `ContextCompactionStore`.
4. On a later turn, your explicit `nodeApplyCompactedContext` applies the artifact to prompt.

No implicit per-turn appending happens unless your node graph includes the context nodes.

## Node Tree Contract (Explicit)

Recommended linear flow:

```text
start
  -> context-apply-compacted
  -> context-before-llm
  -> your-main-llm-node
  -> context-after-llm
  -> context-end-turn
  -> finish
```

Tool-loop flow:

```text
start
  -> context-before-tool-loop
  -> your-tool-loop-node
  -> context-after-tool-loop
  -> finish
```

You can also use the generic staged node API for custom names/stages.

## Public API Surface

- `KoogContextOrchestrator`
- `ContextManagementConfig`
- Node helpers:
  - `nodeApplyCompactedContext`
  - `nodeManageContextBeforeLlm`
  - `nodeManageContextAfterLlm`
  - `nodeManageContextBeforeToolLoop`
  - `nodeManageContextAfterToolLoop`
  - `nodeManageContextEndTurn`
  - `nodeManageContext` (generic staged helper)
- Response envelope:
  - `ContextRunOutput<T>`
  - `ContextRunTelemetry`

## Minimal Integration Example

```kotlin
strategy<MyInput, ContextRunOutput<MyOutput>>("my-agent") {
    val apply by nodeApplyCompactedContext<MyInput>()
    val beforeLlm by nodeManageContextBeforeLlm<MyInput>()
    val main by node<MyInput, ContextRunOutput<MyOutput>>("main") { input ->
        // ... your logic
    }
    val afterLlm by nodeManageContextAfterLlm<ContextRunOutput<MyOutput>>()
    val endTurn by nodeManageContextEndTurn<ContextRunOutput<MyOutput>>()

    edge(nodeStart forwardTo apply)
    edge(apply forwardTo beforeLlm)
    edge(beforeLlm forwardTo main)
    edge(main forwardTo afterLlm)
    edge(afterLlm forwardTo endTurn)
    edge(endTurn forwardTo nodeFinish)
}
```

## Compactor Backend

Compaction execution is backend-driven:

- `DeterministicContextCompactorBackend` (default, no LLM call).
- `LlmAgentContextCompactorBackend` (private isolated LLM agent for compaction).

Example with private agent backend:

```kotlin
ContextManagementConfig(
    maxContextTokens = 128_000,
    requireModelTokenUsage = false,
    compactorBackend = LlmAgentContextCompactorBackend(
        promptExecutor = myPromptExecutor,
        llmModel = myCompactorModel
    )
)
```

## Persistence Model

Two separate persistence concerns:

1. **Compaction artifacts/jobs state**:
   - Stored by `ContextCompactionStore`.
   - Options include:
     - `InMemoryContextCompactionStore`
     - `PersistenceContextCompactionStore` (uses KOOG `PersistenceStorageProvider`).

2. **Context telemetry for UI/history** (remaining percent, risk zone, etc.):
   - Stored in agent checkpoint properties via `ContextCheckpointProperties.merge(...)`.
   - This is done by your checkpoint-save node/util integration.

## Thresholds and Modes

Thresholds are configured in **used percent**.
Equivalent remaining percent is `100 - used`.

Default config values:

- `watchAtUsedPercent = 75`  -> remaining `25%` (watch policy, light by default)
- `warningAtUsedPercent = 79` -> remaining `21%` (warning policy, structured by default)
- `criticalAtUsedPercent = 80` -> remaining `20%` (critical policy, aggressive by default)
- `emergencyAtUsedPercent = 85` -> remaining `15%` (emergency policy)

You can fully customize policies, timing, and even disable levels by setting `mode = CompressionMode.NONE` for a zone policy.

## Telemetry Contract

`ContextRunTelemetry` is non-null by contract:

- `snapshots: StateFlow<ContextSnapshot>`
- `latest: ContextSnapshot`
- `remainingPercentFlow(): Flow<Int>`
- `currentRemainingPercent(): Int`

So UI/state consumers do not need nullable startup handling for orchestrator telemetry.

## Production Recommendations

- Keep context nodes explicit in every strategy graph where you want memory orchestration.
- Use `LlmAgentContextCompactorBackend` with a smaller, fast compactor model.
- Use `PersistenceContextCompactionStore` for durable async queue/artifacts.
- Keep checkpoint save nodes active so telemetry is visible after restarts.
- Run load tests with your real system prompt sizes and turn lengths.

