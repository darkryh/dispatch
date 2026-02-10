package com.ead.dispatch.runtime

import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeAdvancedValidationTest {
    private class SlotMarker

    @Test
    fun `rememberCallback stays valid across structural churn`() {
        val composer = Composer()
        var showPrefix = false
        val calls = mutableListOf<Int>()

        fun compose(input: Int) {
            withComposer(composer) {
                composer.startComposition()
                if (showPrefix) {
                    remember { SlotMarker() }
                }
                val callback = rememberCallback { calls += input }
                callback()
                val counter = remember { mutableStateOf(0) }
                counter.value += 1
                composer.endComposition()
            }
        }

        compose(1)
        showPrefix = true
        compose(2)
        showPrefix = false
        compose(3)

        assertEquals(listOf(1, 2, 3), calls)
    }

    @Test
    fun `launched effect restarts and disposes across key and structure changes`() = runBlocking {
        val composer = Composer()
        var key = "A"
        var showEffect = true
        var showPrefix = false
        var starts = 0
        var disposes = 0

        fun compose() {
            withComposer(composer) {
                composer.startComposition()
                if (showPrefix) {
                    remember { SlotMarker() }
                }
                if (showEffect) {
                    LaunchedEffect(key) {
                        starts += 1
                        try {
                            awaitCancellation()
                        } finally {
                            disposes += 1
                        }
                    }
                }
                composer.endComposition()
            }
        }

        compose()
        waitUntil { starts == 1 }
        assertEquals(0, disposes)

        key = "B"
        compose()
        waitUntil { starts == 2 && disposes == 1 }

        showPrefix = true
        compose()
        delay(20)
        assertTrue(starts == 2 || starts == 3)
        if (starts == 2) {
            assertEquals(1, disposes)
        } else {
            assertTrue(disposes >= 2)
        }

        showEffect = false
        compose()
        waitUntil { disposes == starts }
    }

    @Test
    fun `randomized structural recomposition avoids type collisions`() {
        val composer = Composer()
        val random = Random(0xD15FACEL.toInt())
        var checksum = 0
        var disposeCount = 0

        repeat(5_000) { iteration ->
            val showPrefix = random.nextBoolean()
            val showCallbackState = random.nextBoolean()
            val showEffect = random.nextBoolean()
            val effectKey = if (random.nextBoolean()) "k1" else "k2"

            withComposer(composer) {
                composer.startComposition()

                if (showPrefix) {
                    remember { SlotMarker() }
                }
                if (showCallbackState) {
                    val callbackState = remember { mutableStateOf<Any>({ checksum += 3 }) }
                    callbackState.value = { checksum += 7 }
                }
                if (showEffect) {
                    DisposableEffect(effectKey) {
                        onDispose { disposeCount += 1 }
                    }
                }

                val numericState = remember { mutableStateOf(0) }
                numericState.value = (numericState.value + 1) % 11
                checksum += numericState.value + iteration

                composer.endComposition()
            }
        }

        assertTrue(checksum > 0)
        assertTrue(disposeCount >= 0)
    }

    @Test
    fun `remember performance smoke under stable composition`() {
        val composer = Composer()
        var sink = 0

        val durationMs = measureTimeMillis {
            repeat(20_000) {
                withComposer(composer) {
                    composer.startComposition()
                    repeat(12) { index ->
                        val value = remember(index) { index * 3 }
                        sink += value
                    }
                    composer.endComposition()
                }
            }
        }

        assertTrue(sink > 0)
        assertTrue(durationMs < 30_000, "remember stable smoke test too slow: ${durationMs}ms")
    }

    @Test
    fun `remember performance smoke under structural churn`() {
        val composer = Composer()
        var sink = 0
        var showPrefix = false

        val durationMs = measureTimeMillis {
            repeat(20_000) {
                showPrefix = !showPrefix
                withComposer(composer) {
                    composer.startComposition()
                    if (showPrefix) {
                        remember { SlotMarker() }
                    }
                    val value = remember { mutableStateOf(0) }
                    value.value = (value.value + 1) % 17
                    sink += value.value
                    composer.endComposition()
                }
            }
        }

        assertTrue(sink >= 0)
        assertTrue(durationMs < 30_000, "remember churn smoke test too slow: ${durationMs}ms")
    }

    private suspend fun waitUntil(
        timeoutMs: Long = 1_000,
        pollIntervalMs: Long = 5,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
        }
        assertTrue(condition(), "Condition not met within ${timeoutMs}ms")
    }
}
