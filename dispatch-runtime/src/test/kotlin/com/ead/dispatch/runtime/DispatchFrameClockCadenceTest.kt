package com.ead.dispatch.runtime

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DispatchFrameClockCadenceTest {
    /**
     * Cadence-neutrality guard: DispatchFrameClock introduces NO time/FPS gate.
     *
     * Paints flow through FrameScheduler (the sole paint pacer, which is the only place a
     * `delay`/frame-budget lives) — NOT through this clock. The Option A fix adds only a cooperative
     * `yield()`, which reschedules at the SAME virtual time and never advances the clock.
     *
     * Here, N sequential `withFrameNanos { count++ }` calls all complete within a single
     * `advanceUntilIdle()` while virtual time stays put. If the clock contained any `delay`/FPS gate,
     * either some callbacks would not run without advancing time, or virtual time would have moved.
     * `count == N` AND `currentTime` unchanged proves there is no time gate in the clock.
     */
    @Test
    fun `clock runs all frame callbacks without advancing virtual time`() =
        runTest {
            val n = 50
            var count = 0
            val startTime = testScheduler.currentTime

            // DispatchFrameClock installed in context so withFrameNanos resolves it.
            launch(DispatchFrameClock) {
                repeat(n) {
                    withFrameNanos { count++ }
                }
            }

            advanceUntilIdle()

            assertEquals(n, count, "All $n frame callbacks must run (no FPS/time gate in the clock)")
            assertEquals(
                startTime,
                testScheduler.currentTime,
                "Clock must not advance virtual time (no delay/FPS gate)",
            )
        }
}
