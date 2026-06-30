package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DispatchFrameClockStarvationTest {
    /**
     * Deterministic proof that DispatchFrameClock no longer lets a busy `withFrameNanos` loop
     * monopolize the single cooperative UI thread.
     *
     * A `StandardTestDispatcher` models the real `limitedParallelism(1)` UI dispatcher: only one
     * coroutine runs at a time and a coroutine keeps the slot until it suspends. We install
     * DispatchFrameClock in a scope on that dispatcher, then race two coroutines:
     *  - a "consumer" that increments a counter and cooperatively `yield()`s, and
     *  - a "busy frame loop" that hammers `withFrameNanos {}` (the idiomatic Compose animation shape).
     *
     * The busy loop is launched FIRST so that, without a yield inside the clock, it would run all
     * 1000 iterations before the consumer ever runs.
     *
     * RED before the fix: the pass-through clock (`onFrame(...)` then return, never suspends) means
     * the busy loop never relinquishes the slot, so at the midpoint the consumer has made zero
     * progress -> `midpoint == 0` and the assertion fails.
     * GREEN after the fix: the `yield()` after `onFrame` lets the consumer interleave, so the
     * consumer has progressed well before the busy loop reaches its midpoint -> `midpoint > 0`.
     *
     * The loops are BOUNDED `repeat(N)` (not an infinite `while (isActive)`) so the cooperative
     * scheduler can fully drain under `advanceUntilIdle()`; an unbounded loop would hang virtual
     * time instead of producing a clean assert.
     */
    @Test
    fun `busy frame loop does not starve a cooperative consumer`() =
        runTest {
            val iterations = 1000
            val dispatcher = StandardTestDispatcher(testScheduler)
            // DispatchFrameClock is installed in the scope so the top-level withFrameNanos resolves it.
            val scope = CoroutineScope(dispatcher + DispatchFrameClock)

            val progress = AtomicInteger(0)
            var midpoint = -1

            // Launched FIRST: with a pass-through clock this monopolizes the single slot.
            scope.launch {
                repeat(iterations) { i ->
                    withFrameNanos { /* apply step; no-op for the test */ }
                    if (i == iterations / 2) midpoint = progress.get()
                }
            }
            // Cooperative consumer competing for the same single slot.
            scope.launch {
                repeat(iterations) {
                    progress.incrementAndGet()
                    yield()
                }
            }

            advanceUntilIdle()

            assertTrue(
                midpoint > 0,
                "Consumer was starved by the busy withFrameNanos loop (midpoint=$midpoint); " +
                    "the cooperative yield after onFrame should let it interleave.",
            )
        }

    /**
     * The clock must still behave like a clock: return the callback's result and hand each call a
     * monotonic (non-decreasing) nanosecond timestamp across repeated invocations.
     */
    @Test
    fun `withFrameNanos returns the callback result and supplies monotonic timestamps`() =
        runTest {
            val timestamps = mutableListOf<Long>()

            repeat(5) { i ->
                val result =
                    DispatchFrameClock.withFrameNanos { nanos ->
                        timestamps += nanos
                        "frame-$i"
                    }
                assertEquals("frame-$i", result, "withFrameNanos must return onFrame's result")
            }

            assertEquals(5, timestamps.size)
            for (i in 1 until timestamps.size) {
                assertTrue(
                    timestamps[i] >= timestamps[i - 1],
                    "Timestamps must make forward (monotonic) progress: " +
                        "${timestamps[i]} < ${timestamps[i - 1]}",
                )
            }
        }
}
