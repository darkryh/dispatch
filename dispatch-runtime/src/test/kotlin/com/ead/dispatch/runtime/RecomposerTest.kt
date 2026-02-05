package com.ead.dispatch.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class RecomposerTest {
    @Test
    fun `recomposer invokes callbacks after request`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val recomposer = Recomposer(scope)
        var callbacks = 0
        recomposer.registerComposition { callbacks++ }

        val job = recomposer.start()
        recomposer.requestRecomposition()

        repeat(10) {
            if (callbacks > 0) return@repeat
            yield()
        }

        recomposer.stop()
        job.cancel()
        scope.cancel()

        assertTrue(callbacks > 0)
    }

    @Test
    fun `recomposer only invokes callbacks for invalidated scopes`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val recomposer = Recomposer(scope)
        val scopeA = Any()
        val scopeB = Any()
        var aCalls = 0
        var bCalls = 0

        recomposer.registerComposition(scopeA) { aCalls++ }
        recomposer.registerComposition(scopeB) { bCalls++ }

        val job = recomposer.start()
        recomposer.invalidate(scopeA)

        repeat(10) {
            if (aCalls > 0) return@repeat
            yield()
        }

        recomposer.stop()
        job.cancel()
        scope.cancel()

        assertTrue(aCalls > 0)
        assertEquals(0, bCalls)
    }
}
