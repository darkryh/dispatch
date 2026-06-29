# Application & configuration reference

Package: `com.ead.dispatch.runtime` (unless noted). Modules: `dispatch-core`, `dispatch-runtime`.

Every Dispatch app starts with `DispatchApplication`. It boots the runtime, runs your `config { }`
and `content { }` blocks, drives the compose/render loop, and exits the process.

## DispatchApplication

```kotlin
fun DispatchApplication(
    args: Array<String> = emptyArray(),
    content: DispatchScope.() -> Unit,
)
```

Boots the Dispatch runtime and runs `content` against a [`DispatchScope`](#dispatchscope). Call it
directly from `main`; it blocks until the app exits and calls `exitProcess` with the resolved exit
code.

**Parameters**

- `args` (`Array<String>`) — the process arguments, used to resolve declared flags and arguments.
  Optional; defaults to `emptyArray()`.
- `content` (`DispatchScope.() -> Unit`) — the app builder. Call `config { }` and `content { }` here.

**Example**

```kotlin
import com.ead.dispatch.runtime.DispatchApplication
import com.ead.dispatch.runtime.ExitKeyBinding
import com.ead.dispatch.theme.DispatchTheme

fun main(args: Array<String>) =
    DispatchApplication(args) {
        config {
            name = "my-app"
            version = "1.0.0"
            theme = DispatchTheme.Dark
            exitKeys(ExitKeyBinding.ctrl("C"))
        }
        content {
            App()
        }
    }
```

## DispatchScope

The receiver of the `DispatchApplication` trailing lambda. It exposes the builder blocks and a set
of imperative helpers.

```kotlin
interface DispatchScope {
    val terminal: Terminal
    val theme: DispatchTheme
    val args: Array<String>
    val terminalWidth: Int
    val terminalHeight: Int

    fun config(block: DispatchConfig.() -> Unit)
    fun exit(code: Int = 0)
    fun hasFlag(name: String): Boolean
    fun getArgument(name: String): String?
    fun launch(block: suspend CoroutineScope.() -> Unit): Job
    fun clearScreen(clearScrollback: Boolean = false)
    fun onKeyEvent(handler: (KeyboardEvent) -> Unit)
    fun onMouseEvent(handler: (MouseEvent) -> Unit)
    fun content(block: @DispatchRenderer @Composable () -> Unit)
}
```

**Members**

- `terminal` (`Terminal`) — the underlying Mordant terminal.
- `theme` (`DispatchTheme`) — the resolved theme.
- `args` (`Array<String>`) — the raw process arguments.
- `terminalWidth` / `terminalHeight` (`Int`) — current terminal size in cells.
- `config(block)` — configure the app. See [`DispatchConfig`](#dispatchconfig).
- `content(block)` — set the root composable. The block is annotated `@DispatchRenderer`.
- `exit(code)` — request shutdown with an exit code. Default `0`.
- `hasFlag(name)` / `getArgument(name)` — read parsed CLI flags and arguments.
- `launch(block)` — launch a coroutine on the app scope; returns a `Job`.
- `clearScreen(clearScrollback)` — clear the terminal; optionally clear scrollback too. Default
  `false`.
- `onKeyEvent(handler)` / `onMouseEvent(handler)` — side-channel raw input hooks. `KeyboardEvent`
  and `MouseEvent` are Mordant types. For in-composition key handling, prefer
  [`KeyBindings`](input.md#keybindings).

## DispatchConfig

The receiver of `config { }`. Configure the app's identity, rendering, exit behavior, and CLI
surface here.

```kotlin
class DispatchConfig : DispatchLifecycleHooks
```

### Properties

| Property | Type | Default | Controls |
|---|---|---|---|
| `name` | `String?` | `null` | App name; printed by `--version`, and the window-title fallback. |
| `version` | `String?` | `null` | Version string printed by `--version`. |
| `description` | `String?` | `null` | App description metadata. |
| `theme` | `DispatchTheme` | `DispatchTheme.Dark` | The theme provided via `LocalTheme`. |
| `activeAreaHeight` | `Int` | `12` | Max lines reserved for the bottom in-place active area. |
| `targetFps` | `Int` | `60` | Frame-coalescing target FPS; `0` renders immediately. |
| `windowTitle` | `String?` | `null` | OSC terminal window title (falls back to `name`). |
| `enforceWindowTitle` | `Boolean` | `true` | Re-applies the window title (~every 1s) if something else changes it. |
| `mouseTracking` | `MouseTracking` | `MouseTracking.Off` | Mordant mouse-tracking mode. |
| `requireExitDoublePress` | `Boolean` | `true` | If true, the exit key must be pressed twice to quit. |
| `exitTimeoutOnDoublePress` | `Duration` | `1500.milliseconds` | Window in which the second exit press counts. |
| `exitKeyBindings` | `List<ExitKeyBinding>` | `listOf(ExitKeyBinding.ctrl("C"))` | Bindings that trigger exit. |
| `exitKeyPredicate` | `((KeyboardEvent) -> Boolean)?` | `null` | Custom exit predicate; overrides `exitKeyBindings` when set. |
| `captureSystemOutput` | `Boolean` | `true` | Whether app `stdout`/`stderr` is captured during rendering. |
| `hibernation` | `HibernationConfig` | enabled, 5 min idle | Idle-hibernation settings — see [Idle hibernation](#idle-hibernation). |

Read-only collections populated by the DSL functions below:

- `flags` (`MutableMap<String, FlagDefinition>`)
- `arguments` (`MutableMap<String, ArgumentDefinition>`)

### Functions

```kotlin
fun flag(name: String, shortName: Char? = null, description: String = "")
```
Registers a boolean CLI flag (for example `--verbose` / `-v`).

```kotlin
fun argument(
    name: String,
    shortName: Char? = null,
    description: String = "",
    default: String? = null,
    required: Boolean = false,
)
```
Registers a value-taking CLI argument.

```kotlin
fun exitKeys(vararg bindings: ExitKeyBinding)
```
Replaces `exitKeyBindings` with the supplied bindings.

```kotlin
fun exitKeyPredicate(predicate: (KeyboardEvent) -> Boolean)
```
Sets a custom predicate deciding whether a key event triggers exit.

```kotlin
fun hibernation(block: HibernationConfig.() -> Unit)
```
Configures idle hibernation in place. See [Idle hibernation](#idle-hibernation).

```kotlin
fun onExit(action: () -> Unit)
```
Registers a shutdown callback. Callbacks run in reverse registration order at exit. Inherited from
[`DispatchLifecycleHooks`](#dispatchlifecyclehooks).

### Koin integration

When you depend on `dispatch-koin`, an extension adds the `koin { }` block usable inside `config`:

```kotlin
// package com.ead.dispatch.koin
fun DispatchConfig.koin(
    vararg validateViewModels: KClass<out ViewModel>,
    stopOnExit: Boolean = true,
    appDeclaration: KoinAppDeclaration,
): KoinApplication
```

Starts Koin from the config block, optionally registers a stop-on-exit hook, and validates that the
listed (or auto-registered) view models resolve. See the [Koin reference](koin.md).

## Idle hibernation

After a stretch with no keyboard or mouse input, a Dispatch app drops to a low-resource *hibernate*
state: the paint cadence falls to `idleFps` and rebuildable caches are released. The next input wakes
it instantly. Hibernation is **enabled by default** and is non-destructive — scrollback and
application state are kept. It only affects rendering; it never pauses your coroutines, view models,
or background work. For the rationale, see [The render model](../explanation/render-model.md#idle-hibernation);
for tuning recipes, see [Tune idle hibernation](../how-to/tune-idle-hibernation.md).

Configure it with the `hibernation { }` block inside `config { }`:

```kotlin
import kotlin.time.Duration.Companion.minutes

config {
    hibernation {
        idleTimeout = 2.minutes
        idleFps = 1
    }
}
```

### HibernationConfig

The receiver of `config.hibernation { }`.

| Property | Type | Default | Controls |
|---|---|---|---|
| `enabled` | `Boolean` | `true` | Whether idle hibernation runs at all. |
| `idleTimeout` | `Duration` | `5.minutes` | Inactivity (no input) before hibernating. |
| `idleFps` | `Int` | `1` | Paint cadence while hibernating; caps the frame rate of ongoing animation. |
| `releaseCaches` | `Boolean` | `true` | Release rebuildable caches (markdown render cache, lazy-list heights, frame diff) on hibernate. |
| `requestGc` | `Boolean` | `true` | Hint a GC after caches are released so freed memory is reclaimed. |
| `trimScrollback` | `Boolean` | `false` | Also drop the renderer's scrollback shadow, forcing a full repaint on wake. Clears only the framework's cached view of painted lines — not the terminal's scrollback nor your app's history state. |
| `pollInterval` | `Duration` | `1.seconds` | How often the idle watcher checks for inactivity. |

### HibernationHandle

A read-only view of the runtime's hibernation state, for diagnostics overlays and apps that react to
it. Read it in composition through `LocalHibernation.current`.

```kotlin
interface HibernationHandle {
    val isHibernating: Boolean       // snapshot-backed; recomposes on change
    val idleFps: Int
    val activeFps: Int
    fun idleCountdownMillis(): Long   // ms of inactivity left before hibernating; 0 while hibernating
    fun wakeNow()                     // force an immediate wake
}

val LocalHibernation: ProvidableCompositionLocal<HibernationHandle?>   // null when unavailable
```

`isHibernating` is backed by snapshot state, so reading it in a composable recomposes that composable
when it flips. `idleCountdownMillis()` is time-based — poll it; it does not auto-recompose.

### HibernationRegistry

A process-wide registry of "release on hibernate" callbacks, letting a cache contribute reclaimable
memory to the hibernate path. Releasers run on the UI thread; everything they drop must be cheap to
rebuild lazily on wake.

```kotlin
object HibernationRegistry {
    fun registerReleaser(release: () -> Unit): AutoCloseable   // close the handle to unregister
    fun releaseAll(): Int
}
```

Register from a `DisposableEffect` and close the handle on dispose so a per-instance cache leaves no
dangling releaser. Dispatch's own widget caches register here; you rarely call it directly.

### Diagnostics

When `DISPATCH_DIAGNOSTICS_FILE` is set, hibernation appends JSON-lines events: `hibernate_enter`
(`heapBeforeBytes`, `heapAfterBytes`, `heapFreedBytes`, `idleFps`, …) and `hibernate_exit`
(`hibernatedDurationMs`, `wakeLatencyNanos`, `activeFps`). They are no-ops when the variable is unset
(the production default).

## DispatchLifecycleHooks

```kotlin
interface DispatchLifecycleHooks {
    fun onExit(action: () -> Unit)
}
```

## ExitKeyBinding

```kotlin
data class ExitKeyBinding(
    val key: String,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
)
```

Describes a key combination that quits the app.

**Methods**

- `matches(event: KeyboardEvent): Boolean` — whether a Mordant event matches this binding
  (special-cases Ctrl+C).
- `label(): String` — a human label, for example `"Ctrl+C"`.

**Companion factories**

```kotlin
fun ctrl(key: String): ExitKeyBinding   // ExitKeyBinding(key = key, ctrl = true)
fun ctrl(c: Char): ExitKeyBinding       // ExitKeyBinding(key = c.toString(), ctrl = true)
```

For Alt/Shift combinations, use the constructor directly:
`ExitKeyBinding("Q", alt = true)`.

## CLI flags and arguments

`FlagDefinition` and `ArgumentDefinition` describe a registered flag or argument. They are created
for you by `config.flag(...)` / `config.argument(...)` and read back through `DispatchArgs`.

```kotlin
data class FlagDefinition(val name: String, val shortName: Char?, val description: String)

data class ArgumentDefinition(
    val name: String,
    val shortName: Char?,
    val description: String,
    val default: String?,
    val required: Boolean,
)
```

### DispatchArgs and DispatchContext

```kotlin
data class DispatchArgs(
    val rawArgs: List<String>,
    val flags: Set<String>,
    val arguments: Map<String, String>,
) {
    fun hasFlag(name: String): Boolean
    fun getArgument(name: String): String?
    fun requireArgument(name: String): String   // throws if missing
    fun requireFlag(name: String)                // throws if missing
}

data class DispatchContext(
    val scope: DispatchScope,
    val args: DispatchArgs,
    val config: DispatchConfig,
)
```

Read these inside composition via the helpers in [Composition helpers](#composition-helpers) or the
locals in [Composition locals](#composition-locals).

## Composition helpers

Package `com.ead.dispatch.runtime`. Convenience composables for reading runtime state.

```kotlin
@Composable fun dispatchScope(): DispatchScope
@Composable fun dispatchArgs(): DispatchArgs
@Composable fun dispatchContext(): DispatchContext
@Composable fun dispatchConfig(): DispatchConfig
@Composable fun terminalWidth(): Int
@Composable fun terminalHeight(): Int

@Composable fun <T> rememberState(value: T): MutableState<T>
@Composable fun <T> rememberState(key: Any?, value: T): MutableState<T>
@Composable fun <T> rememberCallback(callback: T): T
```

The `require*` variants throw a clear error when the value is absent:

```kotlin
@Composable fun requireDispatchScope(): DispatchScope
@Composable fun requireDispatchArgs(): DispatchArgs
@Composable fun requireDispatchContext(): DispatchContext
@Composable fun requireArgument(name: String): String
@Composable fun requireFlag(name: String)
```

## Composition locals

Package `com.ead.dispatch.runtime`. Read these with `LocalX.current`.

| Local | Type | Default |
|---|---|---|
| `LocalTerminal` | `Terminal` | required (errors if absent) |
| `LocalTheme` | `DispatchTheme` | `DispatchTheme.Dark` |
| `LocalDispatchScope` | `DispatchScope` | required |
| `LocalDispatchArgs` | `DispatchArgs` | required |
| `LocalDispatchContext` | `DispatchContext` | required |
| `LocalDispatchConfig` | `DispatchConfig` | required |
| `LocalSavedStateHandle` | `SavedStateHandle?` | `null` |
| `LocalFocused` | `Boolean` | `false` |
| `LocalEnabled` | `Boolean` | `true` |
| `LocalContentAlpha` | `Float` | `1.0f` |
| `LocalPosition` | `Position` | `Position(0, 0)` |
| `LocalKeyboardInterceptor` | `KeyboardInterceptor` | required |
| `LocalExitPromptState` | `ExitPromptState` | `ExitPromptState()` |
| `LocalFocusRegistry` | `FocusRegistry` | required |
| `LocalHibernation` | `HibernationHandle?` | `null` |
| `LocalTerminalWidth` | `Int` | `80` |
| `LocalTerminalHeight` | `Int` | `24` |

```kotlin
data class Position(val x: Int, val y: Int)
```

## Saved state

```kotlin
class SavedStateHandle(private val registry: SavedStateRegistry = SavedStateRegistry()) {
    operator fun <T> get(key: String): T?
    operator fun set(key: String, value: Any?)
    fun contains(key: String): Boolean
    inline fun <T> getOrPut(key: String, defaultValue: () -> T): T
    fun remove(key: String)
    fun keys(): Set<String>
}
```

A per-screen key/value store, provided to navigation entries and view models. Navigation routes are
read back from it with `toRoute()` — see the [navigation reference](navigation.md).

## See also

- [Getting started](../getting-started.md) — build your first app.
- [Keyboard input](input.md) — handle keys inside composition.
- [Tune idle hibernation](../how-to/tune-idle-hibernation.md) — change the timeout, keep a screen
  awake, or turn it off.
- [The render model](../explanation/render-model.md) — what `activeAreaHeight`, `targetFps`,
  hibernation, and the exit prompt mean in terms of rendering.
