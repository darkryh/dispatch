# Architecture and modules

This page explains how Dispatch is organized: what each module does, how they depend on one another,
and why the boundaries are drawn where they are. It is background reading, not a task guide.

## A layered library

Dispatch is split into focused modules so you depend only on what you use, and so each layer can be
reasoned about — and tested — on its own. They stack roughly like this:

```
            dispatch-core            ← app entry point, runtime engine, frame scheduling
                  │
   ┌──────────────┼───────────────────────────────┐
   │              │                                │
dispatch-      dispatch-        dispatch-      dispatch-
renderer       widgets          navigation     koin
(ANSI out)        │                  │
                  ▼                  ▼
            dispatch-layout    dispatch-viewmodel ── dispatch-lifecycle
                  │                  │
                  └──── dispatch-runtime ──────────┘
                        (Compose integration, modifiers,
                         constraints, focus, theme, input)
```

`dispatch-runtime` is the foundation: it integrates the Compose runtime, defines the `Modifier`
system, constraints, focus, theme, and the typed input API. Almost everything depends on it.
`dispatch-core` sits on top, owning the app entry point (`DispatchApplication`), the runtime engine,
and frame scheduling — it wires the other modules together into a running app.

## What each module is responsible for

| Module | Responsibility |
|---|---|
| `dispatch-runtime` | Compose integration (composition, applier, frame clock), the `Modifier` chain, `Constraints`, focus, theme, typed keyboard input, and the runtime composition locals. |
| `dispatch-core` | The app entry point `DispatchApplication`, the runtime engine that drives compose + render, and frame scheduling. |
| `dispatch-renderer` | Turning frames into ANSI output: atomic flushing, the append-only scrollback, and the in-place active area. |
| `dispatch-layout` | `Column`, `Row`, `Box`, flow layouts, alignment, arrangement, and the measure/place primitives. |
| `dispatch-widgets` | The widget library — text, inputs, lists, tables, panels, progress, the command palette, and more. |
| `dispatch-navigation` | The typed, serializable back stack, navigation entries, decorators, and per-entry saved state. |
| `dispatch-viewmodel` | The `ViewModel` and MVI base classes and `StateFlow` helpers. |
| `dispatch-lifecycle` | The lifecycle state enum, owner interface, and observable registry. |
| `dispatch-koin` | Koin DI integration and the Koin-backed view-model factory. |
| `dispatch-workspace` | Filesystem watching for live-reload-style workflows. |
| `dispatch-update*` | The update advisor (core) and one provider artifact per channel (GitHub, Brew, Scoop, APT). |

## Enforced module boundaries

Some dependency edges are deliberately forbidden, and the build fails if they appear (the
`verifyModuleBoundaries` task). The rules keep the layering honest:

- `dispatch-navigation` must not depend on `dispatch-renderer` or `dispatch-core`.
- `dispatch-widgets` must not depend on `dispatch-core`.
- `dispatch-renderer` must not depend on `dispatch-core`.

The common thread: lower layers must not reach up into `dispatch-core` (the app runtime) or into the
renderer. Widgets, layout, and navigation are written against `dispatch-runtime` abstractions, not
against the engine that happens to drive them. That is what lets the widget and navigation modules be
tested without booting a terminal, and what keeps `dispatch-core` free to change the engine without
rippling through the UI layers.

## How a frame flows

Putting the modules together, one update cycle looks like this:

1. State changes (a `mutableStateOf`, a view model's `StateFlow`, a key event).
2. The Compose runtime (in `dispatch-runtime`) recomposes the affected composables.
3. Layout (`dispatch-layout`) measures and places the resulting node tree under the current terminal
   constraints.
4. The engine (`dispatch-core`) schedules a frame at the target rate and hands it to the renderer.
5. The renderer (`dispatch-renderer`) computes the minimal update and flushes one atomic frame — see
   [The render model](render-model.md).

Navigation, view models, lifecycle, and DI sit alongside this loop: navigation decides *which*
composables are in the tree, view models hold their state, the lifecycle tracks each entry, and Koin
supplies dependencies.

## Why split it this way

A single monolithic module would be simpler to publish but worse to use and maintain. The split buys
three things: **smaller dependencies** (a non-navigating app never pulls in serialization; an app
without DI never pulls in Koin), **testability** (the widget and navigation layers run in plain unit
tests because they don't depend on the engine or the renderer), and **a stable contract** (the
forbidden-edge checks and a public-API surface guard mean the layers can't quietly entangle).

## See also

- [The render model](render-model.md) — the renderer's invariants in detail.
- [Why the Compose runtime](why-compose-runtime.md) — why `dispatch-runtime` reuses Compose.
- [Reference](../reference/index.md) — the public API of each module.
