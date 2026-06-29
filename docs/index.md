# Dispatch documentation

Dispatch is a declarative terminal UI framework for Kotlin, built on the Jetpack Compose
runtime. You write `@Composable` functions — `Column`, `Row`, `Text`, lists, inputs, navigation,
and view models — and Dispatch renders them to a real terminal, flicker-free.

If you have built a Compose UI on Android or desktop, the model here is the same: state drives
recomposition, layout is a tree of measure/place nodes, and a modifier chain configures each node.
The difference is the target — a terminal, with its own hard constraints (see
[The render model](explanation/render-model.md)).

## Where to start

Pick the doorway that matches what you need right now.

- **Want a project in one click?** The [Dispatch Initializr](https://darkryh.github.io/dispatch/)
  generates a complete, ready-to-run app in your browser — pick a name, package, and version, and
  download it.
- **New to Dispatch?** Start with the [Getting started tutorial](getting-started.md). It walks you
  from an empty project to a running terminal app, one visible step at a time.
- **Trying to do something specific?** The [how-to guides](how-to/index.md) are task-focused
  recipes — add navigation, wire a view model, enable self-update, and more.
- **Looking up an API?** The [reference](reference/index.md) documents every public widget,
  modifier, configuration option, and class, organized by module.
- **Want to understand the design?** The [explanation](explanation/index.md) pages cover the render
  model, the module architecture, and why Dispatch builds on the Compose runtime.

## The four sections

| Section | Read it when you want to… |
|---|---|
| [Tutorials](tutorials/index.md) | Learn Dispatch by building something that works end to end. |
| [How-to guides](how-to/index.md) | Accomplish a specific, real task you already understand. |
| [Reference](reference/index.md) | Look up exact signatures, parameters, defaults, and behavior. |
| [Explanation](explanation/index.md) | Understand how and why Dispatch works the way it does. |

## What Dispatch gives you

- **The Compose programming model** — `@Composable` functions, state and recomposition,
  `CompositionLocal`s, `remember`, and a modifier system, all targeting the terminal.
- **A widget library** — text, buttons, inputs, selectable and multi-select lists, tables, grids,
  trees, panels, surfaces, dividers, progress bars, spinners, checklists, diffs, and a command
  palette. See the [widget reference](reference/widgets.md).
- **Layout** — `Column`, `Row`, `Box`, weighting, alignment, and arrangement, measured in terminal
  cells. See the [layout reference](reference/layout.md).
- **Navigation** — a typed, serializable back stack with lifecycle-aware entries. See the
  [navigation reference](reference/navigation.md).
- **Architecture** — `ViewModel` and MVI base classes, a lifecycle registry, saved-state handles,
  and optional Koin dependency injection.
- **Optional modules** — in-app [self-update](how-to/check-for-updates.md) (Homebrew, Scoop, APT,
  GitHub) and [filesystem watching](how-to/watch-the-workspace.md).

## Requirements

- JDK 21 or newer.
- Kotlin, built with Gradle (the Compose compiler plugin is required — see
  [Getting started](getting-started.md)).

## Project records

- [Changelog](../CHANGELOG.md) — notable changes per release.
- [License](../LICENSE) — Apache License 2.0.
