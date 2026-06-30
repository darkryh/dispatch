# Dispatch vs Android/Compose Investigation

## Objective
Validate how Android and Jetpack Compose internals work (API surface, lifecycle, rendering, layout, state) and identify concrete improvements for Dispatch UI (terminal).

## Current Assumptions
- Focus on runtime, rendering, layout, state, input, navigation, lifecycle, and ViewModel behaviors.
- Compare Dispatch behaviors to Android/Compose reference behavior and identify gaps or refactor targets.
- Pin Android and Compose versions to latest stable once verified from official sources.

## Dispatch Architecture (Current)

**Modules**
- `dispatch-core`: Entry point and app orchestration. See `dispatch-core/src/main/kotlin/com/ead/dispatch/runtime/DispatchApplication.kt`.
- `dispatch-runtime`: Composition engine, state, composition locals, modifiers, and constraints. See `dispatch-runtime/src/main/kotlin/com/ead/dispatch/runtime/DispatchComposition.kt` and `dispatch-runtime/src/main/kotlin/com/ead/dispatch/state`.
- `dispatch-renderer`: Terminal rendering, frame diff, and active-area rendering. See `dispatch-renderer/src/main/kotlin/com/ead/dispatch/render/TerminalRenderer.kt`.
- `dispatch-layout`: Layout primitives (Row/Column/Box) and measure/placement policy. See `dispatch-layout/src/main/kotlin/com/ead/dispatch/layout/Layout.kt`.
- `dispatch-widgets`: UI components built on layout/runtime. See `dispatch-widgets/src/main/kotlin/com/ead/dispatch/widget`.
- `dispatch-navigation`: Back stack, entry lifecycle, saved state, and navigation display. See `dispatch-navigation/src/main/kotlin/com/ead/dispatch/navigation/NavDisplay.kt`.
- `dispatch-viewmodel`: ViewModel base class and composition integration. See `dispatch-viewmodel/src/main/kotlin/com/ead/dispatch/viewmodel/ViewModel.kt`.
- `dispatch-lifecycle`: Minimal lifecycle state registry. See `dispatch-lifecycle/src/main/kotlin/com/ead/dispatch/lifecycle/Lifecycle.kt`.
- `dispatch-koin`: Koin DI integration for dispatch navigation/viewmodel.
- `dispatch-update*`: Update tooling for multiple package managers.

**Runtime + Rendering Flow (Simplified)**
- App entry: `DispatchApplication(...)` configures terminal, input, and recomposition loop.
- Composition: `DispatchScope.renderer { ... }` sets the active UI block.
- Recomposition: `Recomposer` invalidates on state changes and triggers `composeAndRender()`.
- Measure/Layout: composition yields a `Measurable` tree; root is measured with terminal constraints.
- Render split: `splitContentForRendering(...)` divides scrolling history vs active area.
- Render output: `TerminalRenderer` updates active area in-place and appends scrolling content.

**State & Effects (Key Concepts)**
- `mutableStateOf`, `DerivedState`, and `SnapshotMutableState` track reads via recomposer scope.
- `SideEffect`, `LaunchedEffect`, and `DisposableEffect` model non-render effects.

**Input & Focus**
- Raw mode input loop dispatches `KeyboardEvent` and `MouseEvent`.
- `KeyboardInterceptor` allows widgets to intercept events.
- `FocusRegistry` tracks focused elements.

## Investigation Topics (Android/Compose)
- Lifecycle model (state transitions, cleanup timing).
- Rendering scheduling (frame clock, invalidation coalescing).
- Layout pipeline (constraints, measure/placement rules).
- State snapshot model (write policy, consistency, derived state).
- Input routing (focus, key interception, event propagation).
- Saved state (navigation/restoration patterns).
- ViewModel scoping rules (lifecycle & clearing).

## Pinned Versions (Initial)
- Android 15 (API level 35) as the stable baseline.
- Jetpack Compose via Compose BOM `2026.01.00` as the stable Compose baseline.
- Compose 1.9 noted as a stable release in the official release notes (August 2025).

## Local Artifacts (Downloaded)
- Compose AARs in `/tmp/compose-2026.01.00` for runtime, ui, foundation, material, material3, and ui-text (both base and `-android` variants).
- Added `ui-unit-android` for constraints inspection.
- Extracted classes jars under `/tmp/compose-2026.01.00/extract`.

## Compose Findings (Initial)

**Runtime**
- `Recomposer` is a `CompositionContext` that drives recomposition and applies changes. It exposes a state flow, frame callbacks, and recomposition runners.
- `Composer` is a structured engine with groups, slot management, and node operations (`startNode`, `createNode`, `apply`, `recordSideEffect`).
- Snapshot system is first-class. `SnapshotMutableStateImpl` and `DerivedSnapshotState` are backed by snapshot records, and `SnapshotStateObserver` tracks reads for invalidation.

**State**
- Snapshot implementation includes transaction-like snapshots, read/write observers, and conflict handling (`Snapshot`, `SnapshotStateObserver`).
- State uses `SnapshotMutationPolicy` to determine change equivalence and merges.

**Layout**
- `MeasurePolicy` measures with constraints encoded as a packed `long` (not a data class).
- `LayoutNode` is a core UI node with measurement, layout, semantics, focus, and modifier chains.
- `Placeable` is an abstract measured object with size and placement helpers.

**Input/Focus**
- UI layer includes `FocusOwner`, focus invalidation managers, and node-level focus modifiers.

**Foundation**
- `ScrollState` and `LazyListState` implement `ScrollableState` and expose scroll methods, indicators, and saveable state.
- Lazy list stack includes item providers, prefetch strategy, and cache windowing.
- Gesture and bring-into-view infrastructure is layered into foundation.

**Text**
- Text layout is explicit and reusable via `TextMeasurer` and `Paragraph` APIs.
- Text measurement and layout result types are separate from rendering layers.

## Dispatch vs Compose (Early Notes)

**Recomposition**
- Dispatch: `Recomposer` schedules recomposition via a conflated channel and executes callbacks immediately on collection.
- Compose: `Recomposer` integrates with a frame clock and can run recompose/apply loops.

**State & Snapshots**
- Dispatch: `SnapshotMutableState` invalidates scopes directly via reader tracking.
- Compose: snapshot system uses state records and observers with mutation policies.

**Layout**
- Dispatch: constraints are explicit and layout results are lines + sizes.
- Compose: constraints packed into `long`, layout nodes and modifiers are more granular and extensible.

**Input/Focus**
- Dispatch: focus registry + keyboard interceptor are minimal and effective for CLI.
- Compose: focus is part of node system and integrates with semantics and input routing.

## Comparison Matrix (Draft)

| Topic | Compose | Dispatch | Notes / Opportunity |
| --- | --- | --- | --- |
| Recomposition scheduling | `Recomposer` integrates a frame clock and apply loop | `Recomposer` triggers callbacks on a conflated flow | Consider a frame-clock hook or end-of-frame callbacks to coalesce renders |
| State model | Snapshot system with mutation policies and state records | Direct reader tracking, no snapshot isolation | Consider mutation policies or a lightweight snapshot/transaction layer |
| Constraints | Packed `long` with helper accessors | Data class with explicit fields | Dispatch is clearer but potentially heavier; no action required unless perf needs |
| Layout tree | `LayoutNode` graph + modifier/node chain | Measurable tree via layout functions | Consider node-style extensibility if needed for cross-cutting behaviors |
| Intrinsics | Intrinsic measurement APIs in `MeasurePolicy` | Not implemented | Optional; only if widgets need intrinsic sizing |
| Scrolling/Lazy | `ScrollState`, `LazyListState`, prefetch and cache windows | `LazyColumn` exists; no explicit prefetch | Consider cache windowing or prefetch to reduce recomposition churn |
| Text measurement | `TextMeasurer` + `Paragraph` APIs | Text rendering via terminal lines | Optional: add text measurement utilities for advanced layout |
| Focus/input | Focus owners + semantics integration | FocusRegistry + KeyboardInterceptor | Possible enhancement: focus traversal policies and semantic tagging |

## Candidate Spikes (Draft)
- Frame-coalesced recomposition: integrate `RenderLoop` into `DispatchApplication` so invalidations schedule a frame rather than render immediately.
- Mutation policy for state: add optional policy to `mutableStateOf` and `SnapshotMutableState` to control equality and reduce unnecessary recompositions.
- Lazy list cache windowing: add a small cache/prefetch window to `LazyColumn` to reduce remeasure churn for large datasets.

## Next Steps
1. Prototype 1-3 high-value refactors/spikes in Dispatch.
2. Define tests/benchmarks for correctness and performance.
