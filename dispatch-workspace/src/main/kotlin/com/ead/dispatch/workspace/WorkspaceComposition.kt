package com.ead.dispatch.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

val LocalWorkspaceWatcher = compositionLocalOf<WorkspaceWatcher?> { null }
val LocalWorkspaceConfig = compositionLocalOf<WorkspaceWatchConfig?> { null }

@Composable
fun requireWorkspaceWatcher(): WorkspaceWatcher {
    return LocalWorkspaceWatcher.current ?: error("No WorkspaceWatcher provided. Wrap your UI in WorkspaceProvider.")
}

@Composable
fun requireWorkspaceConfig(): WorkspaceWatchConfig {
    return LocalWorkspaceConfig.current ?: error("No WorkspaceWatchConfig provided. Wrap your UI in WorkspaceProvider.")
}

@Composable
fun WorkspaceProvider(
    config: WorkspaceWatchConfig,
    content: @Composable () -> Unit,
) {
    val watcher = rememberWorkspaceWatcher(config)
    CompositionLocalProvider(
        LocalWorkspaceWatcher provides watcher,
        LocalWorkspaceConfig provides config,
    ) {
        content()
    }
}

@Composable
fun rememberWorkspaceWatcher(config: WorkspaceWatchConfig): WorkspaceWatcher {
    val watcher = remember(config) { DefaultWorkspaceWatcher(config) }
    DisposableEffect(watcher) {
        watcher.start()
        onDispose { watcher.stop() }
    }
    return watcher
}

@Composable
fun rememberWorkspaceEvents(config: WorkspaceWatchConfig): Flow<WorkspaceEvent> {
    return rememberWorkspaceWatcher(config).events
}

data class WorkspaceState(
    val latestEvent: WorkspaceEvent? = null,
    val lastEventAt: Long? = null,
    val hasPendingChanges: Boolean = false,
)

@Composable
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
