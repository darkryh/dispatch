# Dispatch

A declarative terminal UI framework for Kotlin, built on the Jetpack Compose runtime.

Dispatch lets you build rich, interactive command-line applications the same way you build
Compose UIs — with `@Composable` functions, state, layout containers, navigation, and
view models — while rendering to a real terminal within its hard constraints.

> Status: `1.0.0-SNAPSHOT`, preparing for first release.

## Why Dispatch

- **Compose programming model** — `Column`, `Row`, `Box`, `LazyColumn`, state, recomposition,
  `CompositionLocal`s, and a familiar modifier system, all targeting the terminal.
- **Flicker-free rendering** — a render-decision engine paints content in a print-once /
  append-only style and only repaints what genuinely changes. Committed scrollback is never
  rewritten; the bottom "active area" updates in place; screen navigation does a deliberate
  full repaint. Every frame is buffered and flushed atomically to avoid tearing.
- **Navigation** — a typed back stack with entry decorators, saved state, and lifecycle-aware
  disposal.
- **Architecture batteries included** — `ViewModel` / MVI base classes, a lifecycle registry,
  saved-state handles, and optional Koin dependency injection.
- **Self-update + workspace** — optional modules for in-app update checks (Homebrew, Scoop,
  APT, GitHub releases) and filesystem watching.

## Modules

| Module | Responsibility |
|---|---|
| `dispatch-core` | Runtime engine, frame scheduling, the render-decision pipeline |
| `dispatch-runtime` | Compose integration, composition, modifiers, constraints, focus |
| `dispatch-renderer` | ANSI output, atomic frame flushing, active-area management |
| `dispatch-layout` | `Column` / `Row` / `Box` / `Layout` measure policies, alignment, arrangement |
| `dispatch-widgets` | `Text`, `LazyColumn`, inputs, lists, grids, panels, and more |
| `dispatch-navigation` | Typed back stack, nav entries, decorators, saved state |
| `dispatch-viewmodel` | `ViewModel`, MVI base classes, `StateFlow` helpers |
| `dispatch-lifecycle` | Lifecycle registry and states |
| `dispatch-koin` | Koin DI integration and view-model factory |
| `dispatch-workspace` | Filesystem watching for live-reload style workflows |
| `dispatch-update*` | Update advisor + provider implementations (Brew/Scoop/APT/GitHub) |

## Quick start

```kotlin
import com.ead.dispatch.runtime.DispatchApplication
import com.ead.dispatch.runtime.ExitKeyBinding
import com.ead.dispatch.theme.DispatchTheme

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config {
            name = "my-app"
            windowTitle = "My App"
            version = "1.0.0"
            theme = DispatchTheme.Dark
            targetFps = 60
            exitKeys(ExitKeyBinding.ctrl("C"))
        }

        content {
            // Any @Composable tree built from Dispatch widgets
            App()
        }
    }
```

See the `dispatch-sample` module for a complete, offline showcase of layout, navigation,
view models, input, and DI.

## Building

```bash
./gradlew build          # compile + test all modules
./gradlew test           # run the test suite
./gradlew :dispatch-sample:run   # run the sample app
./gradlew validateAll    # build, test, detekt, ktlint, coverage, module-boundary checks
```

Requires JDK 21+.

## The render invariant

Dispatch is deliberately built around what a terminal can and cannot do:

- Scrollback is append-only — committed lines are never repainted.
- The bottom active area updates in place without disturbing scrollback.
- A terminal resize forces a full rewrite (cursor positioning is invalidated).
- Screen-to-screen navigation clears the previous screen and repaints fresh, by design.
- Every frame is composed into a single buffer and flushed once to prevent flicker.

Contributions and optimizations must preserve this behavior; see `OPTIMIZATION_PLAN.md`.

## License

Licensed under the [Apache License 2.0](LICENSE).

Copyright 2024 Darkryh.
