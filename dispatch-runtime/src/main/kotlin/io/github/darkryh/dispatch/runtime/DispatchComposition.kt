package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.snapshots.Snapshot
import io.github.darkryh.dispatch.layout.LayoutNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** A retained Dispatch layout composition backed by the official Compose runtime. */
class DispatchComposition(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    onEndChanges: () -> Unit = {},
) : AutoCloseable {
    val root: LayoutNode = LayoutNode("CompositionRoot")

    private val scope = CoroutineScope(dispatcher + SupervisorJob() + DispatchFrameClock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val composition = Composition(DispatchNodeApplier(root, onEndChanges), recomposer)
    private val recomposerJob: Job = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    private val contentState = mutableStateOf<(@Composable () -> Unit)?>(null, neverEqualPolicy())
    private var contentInstalled = false

    fun setContent(content: @Composable () -> Unit) {
        runBlocking(dispatcher) {
            recomposer.awaitIdle()
            contentState.value = content
            if (!contentInstalled) {
                contentInstalled = true
                composition.setContent { contentState.value?.invoke() }
            }
            // Pump until the recomposer drains. A single round may leave follow-up invalidations
            // (e.g. nested state writes during composition) pending; the second guards against them.
            Snapshot.sendApplyNotifications()
            recomposer.awaitIdle()
            Snapshot.sendApplyNotifications()
            recomposer.awaitIdle()
        }
    }

    fun awaitIdle() {
        runBlocking(dispatcher) {
            Snapshot.sendApplyNotifications()
            recomposer.awaitIdle()
        }
    }

    override fun close() {
        composition.dispose()
        recomposer.close()
        runBlocking(dispatcher) { recomposerJob.cancelAndJoin() }
        scope.cancel()
    }
}
