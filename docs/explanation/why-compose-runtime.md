# Why the Compose runtime

Dispatch builds on the Jetpack Compose runtime instead of inventing its own reactive engine. This
page explains what that means and why it is the right foundation for a terminal UI framework. It is
background reading.

## Compose is two things

People say "Compose" to mean two separable pieces:

- **Compose UI** — the Android/desktop toolkit that draws pixels: `androidx.compose.ui`, Material,
  gestures, the works.
- **The Compose runtime** — the engine underneath it: `@Composable` functions, the composer,
  recomposition, `remember`, `mutableStateOf`, snapshots, and `CompositionLocal`s. It knows nothing
  about pixels. It manages a tree of nodes and keeps that tree in sync with your state.

Dispatch uses the **runtime**, not Compose UI. The runtime is a general-purpose tool for building
declarative, state-driven trees of *anything*. For Dispatch, the "anything" is a tree of terminal
layout nodes instead of a tree of pixels.

## What the runtime provides

By adopting the runtime, Dispatch gets — for free, and battle-tested — the hard parts of a reactive
UI system:

- **Recomposition.** When state changes, only the affected composables re-run. You write
  `var count by remember { mutableStateOf(0) }`, change it, and the framework figures out the minimal
  recomposition. Dispatch did not have to build a change-tracking engine.
- **State and snapshots.** `mutableStateOf`, `derivedStateOf`, and the snapshot system give you
  consistent, observable state with the same semantics you know from Compose.
- **`remember` and effects.** `remember`, `LaunchedEffect`, `DisposableEffect`, and friends work
  exactly as they do elsewhere. Dispatch's `KeyBindings`, navigation entries, and view-model scoping
  all lean on them.
- **`CompositionLocal`s.** Theme, terminal size, the keyboard interceptor, the navigator, and the
  view-model provider all flow down the tree as composition locals — the same mechanism Compose uses
  for its own ambient values.

Dispatch supplies the parts the runtime leaves open: a custom `Applier` that builds a tree of
terminal layout nodes, a `MonotonicFrameClock` tied to the terminal's frame schedule, and a renderer
that turns the laid-out tree into ANSI.

## Why this is the right choice

**Familiarity transfers.** If you have written Compose, you already know how to write Dispatch.
`Column`, `Row`, `Box`, `remember`, state hoisting, modifiers, `CompositionLocal`s, view models with
`StateFlow` — the mental model is identical. The only thing that changes is the target and its
constraints.

**It is the correct abstraction.** A terminal UI *is* a declarative, state-driven tree that needs
efficient, incremental updates. That is precisely the problem the Compose runtime was built to solve.
Reusing it means Dispatch's reactivity is as solid as Compose's, because it *is* Compose's.

**The work stays where it matters.** Building a correct recomposition and snapshot system is years of
subtle work. By reusing the runtime, Dispatch spends its effort on what is genuinely terminal-specific
— the [render model](render-model.md), terminal layout and measurement, the widget set, and the input
pipeline — rather than reinventing reactivity.

## The trade-offs

Reusing the runtime is not free of consequences, and they are worth naming:

- **You compile with the Compose compiler plugin.** Dispatch composables are real `@Composable`
  functions, so your build applies the same Kotlin Compose plugin a Compose app would. That is the
  one piece of build setup Dispatch requires.
- **You inherit the runtime's model.** Composition rules, stability, and recomposition semantics are
  Compose's. This is a feature — it is what makes knowledge transfer — but it does mean Dispatch
  follows Compose's conventions rather than defining its own.

For a terminal UI framework, these are small prices for a reactive core that is correct, fast, and
already familiar to a large community of Kotlin developers.

## See also

- [Architecture and modules](architecture.md) — where the runtime integration lives.
- [The render model](render-model.md) — what Dispatch builds *on top of* the runtime.
- [Getting started](../getting-started.md) — see the Compose model in action.
