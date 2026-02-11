package com.ead.dispatch.runtime

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.viewmodel.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class RecompositionScopeIntegrationTest {
    @Test
    fun `flow emission triggers recomposition for scoped composition`() =
        runBlocking {
            val flow = MutableStateFlow(0)
            val composer = Composer()
            val scopeToken = Any()
            val recomposer = Recomposer(CoroutineScope(Dispatchers.Unconfined + SupervisorJob()))

            var latest = -1
            var renderCount = 0

            @Dispatchable
            fun Screen() {
                val state = flow.collectAsState()
                latest = state.value
                renderCount += 1
            }

            fun compose() {
                withComposer(composer) {
                    Recomposer.withRecomposer(recomposer) {
                        Recomposer.withScope(scopeToken) {
                            composer.startComposition()
                            Screen()
                            composer.endComposition()
                            EffectRunner.runPendingEffects()
                        }
                    }
                }
            }

            recomposer.registerComposition(scopeToken) { compose() }
            val job = recomposer.start()

            compose()
            assertEquals(0, latest)
            withTimeout(1_000) { flow.subscriptionCount.first { it > 0 } }

            val initialRenderCount = renderCount
            flow.value = 1

            withTimeout(1_000) {
                while (renderCount == initialRenderCount) {
                    yield()
                }
            }

            assertEquals(1, latest)

            recomposer.stop()
            job.cancel()
        }
}
