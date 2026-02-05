package com.ead.dispatch.runtime

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FrameSchedulerTest {

    @Test
    fun `coalesces multiple requests before a frame runs`() = runTest {
        var frames = 0
        val scheduler = FrameScheduler(
            scope = this,
            targetFps = 30,
            onFrame = { frames++ },
            timeProvider = { testScheduler.currentTime + 1 },
        )

        scheduler.start()
        scheduler.requestFrame()
        scheduler.requestFrame()

        runCurrent()
        scheduler.stop()

        assertEquals(1, frames)
    }

    @Test
    fun `enforces minimum frame spacing`() = runTest {
        var frames = 0
        val scheduler = FrameScheduler(
            scope = this,
            targetFps = 10, // 100ms per frame
            onFrame = { frames++ },
            timeProvider = { testScheduler.currentTime + 1 },
        )

        scheduler.start()

        scheduler.requestFrame()
        runCurrent()
        assertEquals(1, frames)

        scheduler.requestFrame()
        runCurrent()
        assertEquals(1, frames)

        advanceTimeBy(99)
        runCurrent()
        assertEquals(1, frames)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, frames)

        scheduler.stop()
    }

    @Test
    fun `immediate mode renders on each request`() = runTest {
        var frames = 0
        val scheduler = FrameScheduler(
            scope = this,
            targetFps = 0,
            onFrame = { frames++ },
            timeProvider = { testScheduler.currentTime + 1 },
        )

        scheduler.start()

        scheduler.requestFrame()
        runCurrent()
        scheduler.requestFrame()
        runCurrent()

        scheduler.stop()

        assertEquals(2, frames)
    }
}
