package com.ead.dispatch.viewmodel

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.Recomposer
import com.ead.dispatch.runtime.withComposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class StateFlowExtensionsTest {
    @Dispatchable
    private fun CollectState(flow: MutableStateFlow<Int>) {
        flow.collectAsState()
    }

    @Dispatchable
    private fun CollectSideEffect(flow: MutableSharedFlow<Unit>) {
        flow.collectSideEffect { }
    }

    @Test
    fun `collectAsState cancels when composable is removed`() = runBlocking {
        val flow = MutableStateFlow(0)
        val composer = Composer()

        withComposer(composer) {
            composer.startComposition()
            CollectState(flow)
            composer.endComposition()
        }

        withTimeout(1_000) { flow.subscriptionCount.first { it > 0 } }

        withComposer(composer) {
            composer.startComposition()
            composer.endComposition()
        }

        withTimeout(1_000) { flow.subscriptionCount.first { it == 0 } }
    }

    @Test
    fun `collectSideEffect cancels when composable is removed`() = runBlocking {
        val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val composer = Composer()

        withComposer(composer) {
            composer.startComposition()
            CollectSideEffect(flow)
            composer.endComposition()
        }

        withTimeout(1_000) { flow.subscriptionCount.first { it > 0 } }

        withComposer(composer) {
            composer.startComposition()
            composer.endComposition()
        }

        withTimeout(1_000) { flow.subscriptionCount.first { it == 0 } }
    }

    @Test
    fun `collectAsState triggers recomposition on flow update`() = runBlocking {
        val flow = MutableStateFlow(0)
        val composer = Composer()
        val recomposer = Recomposer(CoroutineScope(Dispatchers.Unconfined + SupervisorJob()))
        val scopeToken = Any()

        var renderCount = 0
        var latestValue = -1

        fun compose() {
            withComposer(composer) {
                Recomposer.withRecomposer(recomposer) {
                    Recomposer.withScope(scopeToken) {
                        composer.startComposition()
                        val state = flow.collectAsState()
                        latestValue = state.value
                        renderCount += 1
                        composer.endComposition()
                    }
                }
            }
        }

        recomposer.registerComposition(scopeToken) { compose() }
        val job = recomposer.start()

        compose()
        assertEquals(0, latestValue)
        val initialRenderCount = renderCount

        flow.value = 1

        withTimeout(1_000) {
            while (renderCount == initialRenderCount) {
                yield()
            }
        }

        assertEquals(1, latestValue)

        recomposer.stop()
        job.cancel()
    }
}
