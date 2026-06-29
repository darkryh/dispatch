# Watch the workspace

This guide shows how to react to filesystem changes — for live-reload-style workflows where the UI
updates when files change on disk. It assumes a working app.

Add the dependency:

```kotlin
implementation("io.github.darkryh.dispatch:dispatch-workspace:1.0.0")
```

## Describe what to watch

A `WorkspaceWatchConfig` names the root directory and which paths to include or exclude.

```kotlin
import com.ead.dispatch.workspace.WorkspaceWatchConfig
import java.nio.file.Path

val config = WorkspaceWatchConfig(
    root = Path.of("."),
    includeGlobs = listOf("**/*.kt", "**/*.md"),
    excludeGlobs = listOf(".git/**", "build/**"),
)
```

## React to the latest change

`rememberWorkspaceState` watches the tree and gives you an observable snapshot of the most recent
event. The watcher starts and stops with the composition.

```kotlin
import com.ead.dispatch.workspace.rememberWorkspaceState

@Composable
fun StatusLine() {
    val theme = LocalTheme.current
    val state = rememberWorkspaceState(config)

    if (state.hasPendingChanges) {
        Text("Changed: ${state.latestEvent?.path}", style = theme.warning)
    } else {
        Text("Watching ${config.root}", style = theme.muted)
    }
}
```

## Handle every event

To process each change yourself — rebuild, reload, re-run — collect the event flow with
`rememberWorkspaceEvents`.

```kotlin
import com.ead.dispatch.workspace.WorkspaceEventType
import com.ead.dispatch.workspace.rememberWorkspaceEvents
import androidx.compose.runtime.LaunchedEffect

@Composable
fun Reloader() {
    val events = rememberWorkspaceEvents(config)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event.type) {
                WorkspaceEventType.MODIFIED, WorkspaceEventType.CREATED -> reload(event.path)
                WorkspaceEventType.DELETED -> forget(event.path)
                WorkspaceEventType.OVERFLOW -> reloadEverything()
            }
        }
    }
}
```

`OVERFLOW` means the OS dropped events because they arrived faster than they could be drained — treat
it as "rescan everything."

## Detect content changes, not just touches

To know whether a file's contents actually changed (not just its modified time), enable hashing. Each
event then carries a `hash` you can compare against the previous one.

```kotlin
import com.ead.dispatch.workspace.HashingMode

val config = WorkspaceWatchConfig(
    root = Path.of("."),
    hashing = HashingMode.ON_MODIFY,   // hash only MODIFIED events
)
```

## Share one watcher across a subtree

Install a watcher into composition locals with `WorkspaceProvider`, then reach it from nested
composables with `requireWorkspaceWatcher()`:

```kotlin
import com.ead.dispatch.workspace.WorkspaceProvider
import com.ead.dispatch.workspace.requireWorkspaceWatcher

WorkspaceProvider(config) {
    // anywhere inside:
    val watcher = requireWorkspaceWatcher()
}
```

## Related

- [Workspace watching reference](../reference/workspace.md) — every config option and event type.
