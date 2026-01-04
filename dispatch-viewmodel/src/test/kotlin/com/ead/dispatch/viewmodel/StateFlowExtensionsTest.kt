package com.ead.dispatch.viewmodel

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.withComposer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

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
}
