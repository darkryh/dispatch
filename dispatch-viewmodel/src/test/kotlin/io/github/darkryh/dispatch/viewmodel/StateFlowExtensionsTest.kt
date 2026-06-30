package io.github.darkryh.dispatch.viewmodel

import androidx.compose.runtime.collectAsState
import io.github.darkryh.dispatch.runtime.DispatchComposition
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class StateFlowExtensionsTest {
    @Test
    fun `official collectAsState triggers recomposition and cancels on removal`() =
        runBlocking {
            val flow = MutableStateFlow(0)
            var latestValue = -1
            DispatchComposition().use { composition ->
                composition.setContent { latestValue = flow.collectAsState().value }
                withTimeout(1_000) { flow.subscriptionCount.first { it > 0 } }

                flow.value = 1
                withTimeout(1_000) {
                    while (latestValue != 1) yield()
                }
                assertEquals(1, latestValue)

                composition.setContent { }
                withTimeout(1_000) { flow.subscriptionCount.first { it == 0 } }
            }
        }

    @Test
    fun `collectSideEffect cancels when removed`() =
        runBlocking {
            val flow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
            var latest = 0
            DispatchComposition().use { composition ->
                composition.setContent { flow.collectSideEffect { latest = it } }
                withTimeout(1_000) { flow.subscriptionCount.first { it > 0 } }
                flow.emit(7)
                withTimeout(1_000) { while (latest != 7) yield() }
                composition.setContent { }
                withTimeout(1_000) { flow.subscriptionCount.first { it == 0 } }
            }
        }
}
