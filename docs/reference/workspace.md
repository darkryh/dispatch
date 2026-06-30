# Workspace watching reference

Package: `io.github.darkryh.dispatch.workspace`. Module: `dispatch-workspace`.

The workspace module watches a directory tree and emits filesystem events — for live-reload-style
workflows where the UI reacts to files changing on disk. It is optional; depend on it only if you
need it.

## Quick use in composition

```kotlin
@Composable fun WorkspaceProvider(config: WorkspaceWatchConfig, content: @Composable () -> Unit)
@Composable fun rememberWorkspaceWatcher(config: WorkspaceWatchConfig): WorkspaceWatcher
@Composable fun rememberWorkspaceEvents(config: WorkspaceWatchConfig): SharedFlow<WorkspaceEvent>
@Composable fun rememberWorkspaceState(config: WorkspaceWatchConfig): WorkspaceState
@Composable fun requireWorkspaceWatcher(): WorkspaceWatcher
@Composable fun requireWorkspaceConfig(): WorkspaceWatchConfig
```

`rememberWorkspaceWatcher` creates a watcher and starts/stops it with the composition (via
`DisposableEffect`). `rememberWorkspaceState` collects events into an observable snapshot:

```kotlin
@Composable
fun StatusLine() {
    val state = rememberWorkspaceState(
        WorkspaceWatchConfig(root = Path.of("."), includeGlobs = listOf("**/*.kt")),
    )
    val theme = LocalTheme.current
    if (state.hasPendingChanges) {
        Text("Changed: ${state.latestEvent?.path}", style = theme.warning)
    }
}
```

`WorkspaceProvider` installs a watcher into composition locals (`LocalWorkspaceWatcher`,
`LocalWorkspaceConfig`) so nested composables can call `requireWorkspaceWatcher()` /
`requireWorkspaceConfig()`.

## WorkspaceWatchConfig

```kotlin
data class WorkspaceWatchConfig(
    val root: Path,
    val recursive: Boolean = true,
    val includeGlobs: List<String> = listOf("**"),
    val excludeGlobs: List<String> = listOf(".git/**", "build/**", "**/.gradle/**"),
    val debounce: Duration = 250.milliseconds,
    val hashing: HashingMode = HashingMode.NONE,
    val hashAlgorithm: String = "SHA-256",
    val followSymlinks: Boolean = false,
    val maxEventBatchSize: Int = 512,
)
```

`root` is a `java.nio.file.Path`. Globs filter which paths emit events. `debounce` coalesces rapid
changes. Enable `hashing` to include a content hash on events.

```kotlin
enum class HashingMode { NONE, ON_MODIFY, ALWAYS }
```

- `NONE` — never hash.
- `ON_MODIFY` — hash only `MODIFIED` events.
- `ALWAYS` — hash every event except `DELETED`.

## WorkspaceEvent

```kotlin
data class WorkspaceEvent(
    val path: Path,
    val type: WorkspaceEventType,
    val timestamp: Long,
    val hash: String? = null,
)

enum class WorkspaceEventType { CREATED, MODIFIED, DELETED, OVERFLOW }
```

A single observed change. `OVERFLOW` means the OS dropped events because they arrived faster than
they could be drained.

## WorkspaceState

```kotlin
data class WorkspaceState(
    val latestEvent: WorkspaceEvent? = null,
    val lastEventAt: Long? = null,
    val hasPendingChanges: Boolean = false,
)
```

A snapshot derived from the most recent event, returned by `rememberWorkspaceState`.

## The watcher

```kotlin
interface WorkspaceWatcher {
    val events: SharedFlow<WorkspaceEvent>
    fun start()
    fun stop()
}

class DefaultWorkspaceWatcher(
    config: WorkspaceWatchConfig,
    clock: () -> Long = System::currentTimeMillis,
) : WorkspaceWatcher, AutoCloseable
```

`DefaultWorkspaceWatcher` is `WatchService`-backed. Observe `events` (a `SharedFlow`, so every
collector sees every event) and call `start()` / `stop()` — or let `rememberWorkspaceWatcher` manage
that. Events are emitted with `DROP_OLDEST` overflow handling, sized by `maxEventBatchSize`.

```kotlin
val watcher = DefaultWorkspaceWatcher(config)
watcher.start()
scope.launch { watcher.events.collect { event -> handle(event) } }
// later
watcher.stop()
```

Enable verbose watcher logging with the JVM flag `-Ddispatch.workspace.debug=true`.

## See also

- [Watch the workspace](../how-to/watch-the-workspace.md) — a task-focused guide.
