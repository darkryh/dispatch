package io.github.darkryh.dispatch.runtime

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Deterministic behavioral tests for [HibernationController].
 *
 * The idle clock ([nanoTime]) is driven by a mutable `now` and is independent of the coroutine
 * virtual clock, so each test advances BOTH: it bumps `now` past the idle timeout and advances
 * virtual time so the watcher's `delay(pollInterval)` actually ticks and observes the elapsed idle.
 * A single [StandardTestDispatcher]-backed [TestScope] is shared by the watcher and UI scopes so the
 * `uiScope.launch { enterHibernate() }` body runs on the same controllable dispatcher.
 */
class HibernationControllerTest {
    private companion object {
        val IDLE_TIMEOUT = 50.milliseconds
        val POLL_INTERVAL = 10.milliseconds
        const val AWAKE_FPS = 60
        const val IDLE_FPS = 1
    }

    private class Harness(
        val controller: HibernationController,
        val scheduler: FrameScheduler,
        val releaseCount: AtomicInteger,
        val nowHolder: LongArray,
    ) {
        var now: Long
            get() = nowHolder[0]
            set(value) {
                nowHolder[0] = value
            }
    }

    private fun TestScope.buildHarness(
        enabled: Boolean = true,
        releaseCaches: Boolean = true,
    ): Harness {
        val nowHolder = longArrayOf(0L)
        val releaseCount = AtomicInteger(0)
        val config =
            HibernationConfig().apply {
                this.enabled = enabled
                idleTimeout = IDLE_TIMEOUT
                pollInterval = POLL_INTERVAL
                idleFps = IDLE_FPS
                this.releaseCaches = releaseCaches
                requestGc = false
            }
        val scheduler =
            FrameScheduler(
                scope = this,
                targetFps = AWAKE_FPS,
                onFrame = {},
                timeProvider = { testScheduler.currentTime + 1 },
            )
        val controller =
            HibernationController(
                config = config,
                watcherScope = this,
                uiScope = this,
                frameScheduler = scheduler,
                awakeFps = AWAKE_FPS,
                onReleaseResources = { releaseCount.incrementAndGet() },
                nanoTime = { nowHolder[0] },
            )
        return Harness(controller, scheduler, releaseCount, nowHolder)
    }

    @Test
    fun `enters hibernation once idle past the timeout`() =
        runTest {
            val h = buildHarness()
            h.controller.start()

            // Drive the idle clock past the timeout, then let the watcher poll.
            h.now = IDLE_TIMEOUT.inWholeNanoseconds + 1
            advanceTimeBy(POLL_INTERVAL.inWholeMilliseconds * 2)
            runCurrent()

            assertTrue(h.controller.isHibernating, "should be hibernating after idle timeout")
            assertEquals(IDLE_FPS, h.scheduler.targetFps, "frame rate should drop to idle FPS")
            assertEquals(1, h.releaseCount.get(), "resources should be released once")

            h.controller.stop()
        }

    @Test
    fun `user activity wakes from hibernation and restores FPS`() =
        runTest {
            val h = buildHarness()
            h.controller.start()

            h.now = IDLE_TIMEOUT.inWholeNanoseconds + 1
            advanceTimeBy(POLL_INTERVAL.inWholeMilliseconds * 2)
            runCurrent()
            assertTrue(h.controller.isHibernating)

            h.controller.onUserActivity(h.now)
            runCurrent()

            assertFalse(h.controller.isHibernating, "user activity should wake it")
            assertEquals(AWAKE_FPS, h.scheduler.targetFps, "awake FPS should be restored")

            h.controller.stop()
        }

    @Test
    fun `disabled config never hibernates`() =
        runTest {
            val h = buildHarness(enabled = false)
            h.controller.start()

            // Well past the timeout, with plenty of poll ticks.
            h.now = IDLE_TIMEOUT.inWholeNanoseconds * 100
            advanceTimeBy(POLL_INTERVAL.inWholeMilliseconds * 50)
            runCurrent()

            assertFalse(h.controller.isHibernating)
            assertEquals(AWAKE_FPS, h.scheduler.targetFps)
            assertEquals(0, h.releaseCount.get())

            h.controller.stop()
        }

    @Test
    fun `releaseCaches false hibernates without releasing resources`() =
        runTest {
            val h = buildHarness(releaseCaches = false)
            h.controller.start()

            h.now = IDLE_TIMEOUT.inWholeNanoseconds + 1
            advanceTimeBy(POLL_INTERVAL.inWholeMilliseconds * 2)
            runCurrent()

            assertTrue(h.controller.isHibernating, "should still hibernate")
            assertEquals(IDLE_FPS, h.scheduler.targetFps, "FPS should still drop")
            assertEquals(0, h.releaseCount.get(), "resources must NOT be released")

            h.controller.stop()
        }

    @Test
    fun `idleCountdownMillis decreases with elapsed idle and is zero while hibernating`() =
        runTest {
            val h = buildHarness()
            h.controller.start()

            // Full budget remaining at zero idle.
            assertEquals(IDLE_TIMEOUT.inWholeMilliseconds, h.controller.idleCountdownMillis())

            h.now = 20.milliseconds.inWholeNanoseconds
            val afterTwenty = h.controller.idleCountdownMillis()
            assertEquals(30L, afterTwenty)

            h.now = 40.milliseconds.inWholeNanoseconds
            val afterForty = h.controller.idleCountdownMillis()
            assertEquals(10L, afterForty)
            assertTrue(afterForty < afterTwenty, "countdown should decrease as idle grows")

            // Cross the timeout and let it hibernate.
            h.now = IDLE_TIMEOUT.inWholeNanoseconds + 1
            advanceTimeBy(POLL_INTERVAL.inWholeMilliseconds * 2)
            runCurrent()
            assertTrue(h.controller.isHibernating)
            assertEquals(0L, h.controller.idleCountdownMillis(), "countdown is 0 while hibernating")

            h.controller.stop()
        }
}
