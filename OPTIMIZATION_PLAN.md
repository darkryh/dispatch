# Dispatch — Pre-Release Optimization Plan

> Status legend: `[ ]` pending · `[~]` in progress · `[x]` done · `KEEP` protected (do not change) · `REJECT` deliberately not done
>
> Guiding constraint: **the print-once / append-only terminal render model is correct and must be preserved.** Every change below is internal (allocation, CPU, threading of *non-visual* work) and is intended to be **byte-stream and frame-timing neutral**. Anything that changes *what* the terminal sees, *when* a frame is emitted, or output ordering is treated as render-affecting and gated behind tests.

This plan was produced by a multi-agent audit (7 clusters, adversarially verified) and condensed here. Each item carries its verified `file:line`, impact, and risk.

---

## Protected invariants — KEEP (never "optimize" these)

| Behavior | Location | Why it must stay |
|---|---|---|
| Screen navigation = full wipe + repaint | `RenderPipeline.kt:66` (`screenTransition` → `FULL_REWRITE`, `clearScrollback=true`) | Terminals cannot partially reconcile two unrelated screens. **Intended behavior.** |
| Print-once / append-only scrollback | `ScrollingContentTracker` + `APPEND_ONLY` path | Scrolled-off history is unrepaintable. |
| NOOP / ACTIVE_ONLY same-screen avoidance | `classifyRenderDecision` (`RenderPipeline.kt:175`) | This is what eliminates blinking. |
| Resize → forced full rewrite | `forceRewrite` → `FULL_REWRITE` | Resize invalidates cursor positioning. |
| Atomic single-flush per frame | `flushBuffer` (one `StringBuilder` → one `rawPrint`) | Non-atomic writes cause flicker. |
| Full re-measure+rasterize before NOOP decision | `RenderPipeline.kt:50` | **REJECT caching this** — the fresh measure is the correctness basis of the repaint decision. |

---

## Implementation status (this pass)

All changes below were applied to the working tree and validated by the **existing full test
suite** (every module green after each tier). Byte-neutrality is asserted by the existing
renderer/layout/widget/nav tests, which already check emitted output. Deferred items are listed
with rationale.

- **Phase 1 (Tier 1): DONE** — all 14 items (T1.1–T1.14).
- **Phase 2 (Tier 2): DONE except 3 deferrals** — applied T2.1, T2.3, T2.4, T2.6 (weights), T2.7,
  T2.8, T2.9, T2.10, T2.11. Deferred/rejected: **T2.2** (reverted — see below), **T2.5** and the
  **IntArray `arrange`** half of T2.6 (low-value public-surface churn), **T2.12** (needs golden
  wrap tests).
- **Phase 3 (Tier 3): DONE except T3.5** — applied T3.1, T3.2, T3.3, T3.4 (intents). Deferred:
  **T3.5** (needs the Phase 0 benchmark; cadence-sensitive).
- **Release blockers: R.1 + R.3 DONE.** R.2/P0.3 and R.4 documented below.

### Deferred / reverted — with rationale

- **T2.2 (FocusRegistry skip-when-unchanged) — REVERTED.** A dirty-bit that only the production
  applier sets silently breaks the established `sync()` contract that ~20 test harnesses (and any
  external consumer) rely on: they call `sync(root)` directly after each render and expect it to
  always rebuild. T1.4 already removed the per-frame allocations (the real win); the residual is a
  cheap allocation-free walk. Not worth changing a public contract pre-release.
- **T2.5 (`Constraints.constrain` non-Pair) — SKIPPED.** No production/hot-path caller (tests only);
  changing a public signature for zero runtime benefit is churn the audit itself recommended skipping.
- **T2.6 IntArray `arrange` overload — SKIPPED.** Adds a method to the public sealed `Arrangement`
  interface for a low-value gain; the boxing win that mattered (weights) was taken via `FloatArray`.
- **T2.12 (InputEditor O(n²) cursor) — DEFERRED.** Measurement-CPU only (not memory/render), win
  bounded to arrow-key presses, and the rewrite risks cursor wrap-boundary correctness that needs
  golden tests (P0.1) we have not built yet.
- **T3.5 (`DispatchFrameClock` yield) — DEFERRED.** Frame cadence is part of the render invariant;
  the audit marked it `needs-benchmark`. Must wait for P0.2.

## Phase 0 — Safety net (recommended next; unlocks the deferrals)

- [ ] **P0.1** Golden ANSI byte-stream regression suite for the 5 canonical scenarios (append-scroll, viewport rewrite, active-area update, resize, shell handoff) — capture exact emitted bytes via a recording terminal. (Unlocks T2.12.)
- [ ] **P0.2** Microbenchmark module (kotlinx-benchmark/JMH) covering: measure pass, `FocusRegistry.sync`, `ScrollingContentTracker` append, `flushBuffer`. (Unlocks T3.5.)
- [ ] **P0.3** Apply `binary-compatibility-validator` to public modules and commit `.api` baselines. (Captures the ABI-visible T2.4 placement-flatten change.)

---

## Phase 1 — Tier 1: pure allocation/CPU wins, output-neutral (`adopt`, low risk)

- [ ] **T1.1** Gate per-frame diagnostics so nothing is built when disabled — add `RenderDiagnostics.isEnabled`; guard call sites in `RenderPipeline.kt:130`, `TerminalRenderer.flushBuffer:328`, `RenderDecisionTelemetry.record:25`. Removes a per-frame full-scrollback `hashCode()` and `buffer.toString().toByteArray()`.
- [ ] **T1.2** `viewportLineCount` arithmetic instead of `takeLast` sublist — `RenderPipeline.kt:84` / `DispatchApplication.kt:623`.
- [ ] **T1.3** `ScrollingContentTracker` incremental append (`addAll(linesToAppend)` not `clear()+addAll(all)`) — `ScrollingContentTracker.kt:29`.
- [ ] **T1.4** `FocusRegistry.sync`: reuse scratch HashSet; replace `allOf<>()` per node with direct `foldIn` — `FocusRegistry.kt:30,77`.
- [ ] **T1.5** `paintLine` lazy `StringBuilder?` (allocate only on first ESC byte) — `Layout.kt:196`.
- [ ] **T1.6** Grid row `subList` instead of `drop().take()` (O(n²)→O(n)) — `Grid.kt:121`.
- [ ] **T1.7** Text sentinel-strip: hoist constant, skip on markdown path, guard with `indexOf` — `Text.kt:117`.
- [ ] **T1.8** Cache route-payload JSON on `NavEntryState`, write only on change — `NavEntryDecorator.kt:215`.
- [ ] **T1.9** `rememberNavEntries` / `rememberDecoratedNavEntries`: short-circuit diff when back stack unchanged — `NavEntryDecorator.kt:64,238`.
- [ ] **T1.10** Canvas `CharArray(width).also{it.fill(' ')}` — `Layout.kt:53`.
- [ ] **T1.11** Box/alignment: call `horizontal.align`/`vertical.align` directly, drop `Pair` — `Box.kt:115`, single-pass max loop `Box.kt:87`.
- [ ] **T1.12** Single-pass propagated start-line merge — `Layout.kt:165`.
- [ ] **T1.13** `hashFile` → `java.util.HexFormat` (preserve lowercase/zero-pad) — `WorkspaceWatcher.kt:232`.
- [ ] **T1.14** Dead code: remove `RenderDecisionTelemetry.snapshot()/reset()` + unread counters; remove `Constraints.copyCoerced`.

---

## Phase 2 — Tier 2: stateful reuse, buffers & caches (`adopt-with-care`, low risk)

- [ ] **T2.1** Reused scratch `StringBuilder` in renderer (must stay inside `renderLock`) — `TerminalRenderer.kt:57,76,134,157,172`.
- [ ] **T2.2** `FocusRegistry` skip-when-tree-unchanged via dirty bit from `DispatchNodeApplier.onEndChanges` — `FocusRegistry.kt:29`.
- [ ] **T2.3** Bound `stableContentKeyCache` (LRU ~256; currently unbounded global leak) — `NavEntry.kt:25`.
- [ ] **T2.4** Flatten `SimplePlacementScope` Pair tuples; read `placeable.x/y` (ABI-visible) — `Measurable.kt:163`, `Layout.kt:153`.
- [ ] **T2.5** `Constraints.constrain` non-Pair return (ABI-visible) — `Constraints.kt:108`.
- [ ] **T2.6** Row/Column weights: `FloatArray` 0f-sentinel; `IntArray` arrange overload — `Column.kt:88,172`, `Row.kt:87,174`, `Arrangement.kt`.
- [ ] **T2.7** `staticCompositionLocalOf` for the 5 reference-stable locals — `CompositionLocals.kt:11`.
- [ ] **T2.8** `remember` root `DispatchContext` — `DispatchApplication.kt:231`.
- [ ] **T2.9** `composableContainer`: split `set()` to drop the `Pair` — `ComposerHelpers.kt:54`.
- [ ] **T2.10** Lazy GitHub `HttpClient` confined to owned-default path — `GithubReleaseUpdateProvider.kt:24`.
- [ ] **T2.11** Markdown render cache keyed on text+width+style+**terminal capabilities**, synchronized — `Text.kt:163`.
- [ ] **T2.12** InputEditor: collapse O(n²) measurement renders, keep cursor-marker wrap behavior — `InputEditor.kt:318`.

---

## Phase 3 — Tier 3: concurrency / threading (`breaksRender:true` by definition — one at a time, behind tests)

- [ ] **T3.1** `bell()` under `renderLock` — `TerminalRenderer.kt:420`.
- [ ] **T3.2** Move blocking update-check off the render thread (`withContext(Dispatchers.IO)`) — `UpdateComposable.kt:24`.
- [ ] **T3.3** Replace 50ms exit busy-poll with `CompletableDeferred`; complete from every exit path — `TerminalSessionCoordinator.kt:62`, `DispatchApplication.kt`.
- [ ] **T3.4** MVI emit ordering/allocation: `Channel(UNLIMITED)` (no-drop) instead of `launch{emit}` — `ViewModel.kt:197,226,253,267`.
- [ ] **T3.5** `DispatchFrameClock.withFrameNanos` starvation — **REQUIRES P0.2 benchmark**; must not change cadence — `DispatchFrameClock.kt:7`.

---

## Phase 4 — REJECT / hold the line

- `REJECT` Caching the per-frame measure/rasterize — correctness basis of NOOP decision.
- `REJECT` `ConstraintValidator` fast-path — function has zero production callers (dead).
- `REJECT` `LifecycleRegistry.moveTo` copy avoidance — stdlib `toList()` already no-allocs for size 0/1.

---

## Release blockers (gate publish; not optimizations)

- [x] **R.1 DONE** Maven coordinates changed `com.github.ajalt.mordant.dispatch` → `io.github.darkryh.dispatch` in `build.gradle.kts`. **Confirm the final group before the first non-SNAPSHOT release.**
- [ ] **R.2 DEFERRED** Apply `binary-compatibility-validator` + baseline (same as P0.3). Infra task; captures the one ABI-visible change (T2.4 `getPlacements` return type).
- [x] **R.3 DONE** Added `LICENSE` (Apache-2.0), `README.md`, `CHANGELOG.md`.
- [ ] **R.4 SKIPPED** Catalog pruning is cosmetic and **risky** — the `dispatch-sample` actually uses `sqldelight` (generated code present), so several "unused" catalog entries are used by the sample. Needs careful per-entry verification; no functional/perf impact. Not done to avoid breaking the sample build.

---

## Verification protocol per change

1. `./gradlew compileKotlin` green.
2. Affected module tests green (existing suite is the first safety net; P0.1 golden suite is the byte-level net).
3. For ABI-visible items: review `.api` diff.
4. For Tier 3: full suite + targeted concurrency/timing test.
