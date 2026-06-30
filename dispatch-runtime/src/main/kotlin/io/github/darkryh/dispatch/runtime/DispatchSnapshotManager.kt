package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Delivers global snapshot writes to recomposers hosted outside a Compose platform. */
class DispatchSnapshotManager(
    scope: CoroutineScope,
) : AutoCloseable {
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private val observer = Snapshot.registerGlobalWriteObserver { writes.trySend(Unit) }
    private val job: Job =
        scope.launch {
            for (ignored in writes) {
                Snapshot.sendApplyNotifications()
            }
        }

    override fun close() {
        observer.dispose()
        writes.close()
        job.cancel()
    }
}
