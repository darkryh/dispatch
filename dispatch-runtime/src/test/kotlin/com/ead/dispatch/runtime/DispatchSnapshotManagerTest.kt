package com.ead.dispatch.runtime

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class DispatchSnapshotManagerTest {
    @Test
    fun `global state writes produce apply notifications`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val manager = DispatchSnapshotManager(scope)
        val state = mutableStateOf(0)
        val applied = CompletableDeferred<Unit>()
        val observer = Snapshot.registerApplyObserver { changed, _ ->
            if (state in changed) applied.complete(Unit)
        }

        try {
            state.value = 1
            withTimeout(1.seconds) { applied.await() }
            assertEquals(1, state.value)
        } finally {
            observer.dispose()
            manager.close()
            scope.cancel()
        }
    }
}
