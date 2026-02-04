package com.ead.dispatch.workspace

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.compositionLocalOf
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.ead.dispatch.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

val LocalWorkspaceWatcher = compositionLocalOf<WorkspaceWatcher?> { null }
val LocalWorkspaceConfig = compositionLocalOf<WorkspaceWatchConfig?> { null }

fun requireWorkspaceWatcher(): WorkspaceWatcher {
    return LocalWorkspaceWatcher.current ?: error("No WorkspaceWatcher provided. Wrap your UI in WorkspaceProvider.")
}

fun requireWorkspaceConfig(): WorkspaceWatchConfig {
    return LocalWorkspaceConfig.current ?: error("No WorkspaceWatchConfig provided. Wrap your UI in WorkspaceProvider.")
}

@Dispatchable
fun WorkspaceProvider(
    config: WorkspaceWatchConfig,
    content: @Dispatchable () -> Unit,
) {
    val watcher = rememberWorkspaceWatcher(config)
    CompositionLocalProvider(
        LocalWorkspaceWatcher provides watcher,
        LocalWorkspaceConfig provides config,
    ) {
        content()
    }
}

@Dispatchable
fun rememberWorkspaceWatcher(config: WorkspaceWatchConfig): WorkspaceWatcher {
    val watcher = remember(config) { DefaultWorkspaceWatcher(config) }
    DisposableEffect(watcher) {
        watcher.start()
        onDispose { watcher.stop() }
    }
    return watcher
}

@Dispatchable
fun rememberWorkspaceEvents(config: WorkspaceWatchConfig): Flow<WorkspaceEvent> {
    return rememberWorkspaceWatcher(config).events
}

data class WorkspaceState(
    val latestEvent: WorkspaceEvent? = null,
    val lastEventAt: Long? = null,
    val hasPendingChanges: Boolean = false,
)

@Dispatchable
fun rememberWorkspaceState(config: WorkspaceWatchConfig): WorkspaceState {
    val watcher = rememberWorkspaceWatcher(config)
    var state by remember { mutableStateOf(WorkspaceState()) }

    LaunchedEffect(watcher) {
        watcher.events.collect { event ->
            state = WorkspaceState(
                latestEvent = event,
                lastEventAt = event.timestamp,
                hasPendingChanges = true,
            )
        }
    }

    return state
}
