package com.ead.dispatch.runtime

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the idle-hibernation hooks on [FrameScheduler]: runtime FPS changes via [setTargetFps] and
 * the budget-skipping wake path via [requestImmediateFrame].
 */
class FrameSchedulerPacingTest {
    @Test
    fun `setTargetFps updates the read-only targetFps property`() =
        runTest {
            val scheduler =
                FrameScheduler(
                    scope = this,
                    targetFps = 60,
                    onFrame = {},
                    timeProvider = { testScheduler.currentTime + 1 },
                )

            assertEquals(60, scheduler.targetFps)

            scheduler.setTargetFps(1)
            assertEquals(1, scheduler.targetFps)

            scheduler.setTargetFps(60)
            assertEquals(60, scheduler.targetFps)
        }

    @Test
    fun `requestFrame is a no-op until started`() =
        runTest {
            val frames = AtomicInteger(0)
            val scheduler =
                FrameScheduler(
                    scope = this,
                    targetFps = 60,
                    onFrame = { frames.incrementAndGet() },
                    timeProvider = { testScheduler.currentTime + 1 },
                )

            scheduler.requestFrame()
            runCurrent()

            assertEquals(0, frames.get())
        }

    @Test
    fun `requestFrame is throttled by the frame budget`() =
        runTest {
            val frames = AtomicInteger(0)
            val scheduler =
                FrameScheduler(
                    scope = this,
                    targetFps = 1, // 1000ms per frame
                    onFrame = { frames.incrementAndGet() },
                    timeProvider = { testScheduler.currentTime + 1 },
                )

            scheduler.start()
            // Establish a recent paint so the next request must wait out the budget.
            scheduler.markFrame()

            scheduler.requestFrame()
            runCurrent()
            assertEquals(0, frames.get())

            advanceTimeBy(999)
            runCurrent()
            assertEquals(0, frames.get())

            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, frames.get())

            scheduler.stop()
        }

    @Test
    fun `requestImmediateFrame renders without waiting out the budget`() =
        runTest {
            val frames = AtomicInteger(0)
            val scheduler =
                FrameScheduler(
                    scope = this,
                    targetFps = 1, // 1000ms per frame
                    onFrame = { frames.incrementAndGet() },
                    timeProvider = { testScheduler.currentTime + 1 },
                )

            scheduler.start()
            // A recent paint would normally force a 1000ms wait before the next frame.
            scheduler.markFrame()
            runCurrent()

            val timeBefore = testScheduler.currentTime
            scheduler.requestImmediateFrame()
            runCurrent()

            // Fired immediately, with zero virtual-time advance.
            assertEquals(1, frames.get())
            assertEquals(timeBefore, testScheduler.currentTime)

            scheduler.stop()
        }
}
