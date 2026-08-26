package io.github.darkryh.dispatch.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [FrameScheduler.stopAndJoin] is the primitive shutdown depends on, and the difference between it
 * and plain [FrameScheduler.stop] is the whole fix for the intermittent crash on quit.
 *
 * `onFrame` is a blocking call with no suspension points — it walks the layout tree and writes to
 * the terminal. `job.cancel()` therefore cannot interrupt a frame that has already started; it only
 * stops the next one. Anything that tears down what `onFrame` reads must wait for the current frame
 * to actually finish, or the two race on the same data.
 */
class FrameSchedulerStopAndJoinTest {
    @Test
    fun `stop returns while a frame is still running`() {
        withScheduler { scheduler, frameEntered, releaseFrame, framesCompleted ->
            scheduler.start()
            scheduler.requestFrame()
            assertTrue(frameEntered.await(5, TimeUnit.SECONDS), "the frame never started")

            scheduler.stop()

            // This is exactly the hazard: stop() has returned, so the caller believes it is safe to
            // tear down, while the frame is demonstrably still inside onFrame.
            assertEquals(0, framesCompleted.get())
            releaseFrame.countDown()
        }
    }

    @Test
    fun `stopAndJoin waits for a frame that is already running`() =
        withScheduler { scheduler, frameEntered, releaseFrame, framesCompleted ->
            scheduler.start()
            scheduler.requestFrame()
            assertTrue(frameEntered.await(5, TimeUnit.SECONDS), "the frame never started")

            val joinReturned = AtomicBoolean(false)
            val joiner =
                Thread {
                    runBlocking { scheduler.stopAndJoin() }
                    joinReturned.set(true)
                }.apply { start() }

            // While the frame is held, stopAndJoin must not have returned.
            Thread.sleep(200)
            assertFalse(joinReturned.get(), "stopAndJoin returned while a frame was still in flight")

            releaseFrame.countDown()
            joiner.join(5_000)

            assertTrue(joinReturned.get(), "stopAndJoin never returned after the frame finished")
            assertEquals(1, framesCompleted.get(), "the in-flight frame must have completed first")
        }

    @Test
    fun `stopAndJoin is safe to call twice`() =
        withScheduler { scheduler, _, releaseFrame, _ ->
            releaseFrame.countDown()
            scheduler.start()
            runBlocking {
                scheduler.stopAndJoin()
                scheduler.stopAndJoin()
            }
        }

    @Test
    fun `no further frame starts after stopAndJoin`() =
        withScheduler { scheduler, _, releaseFrame, framesCompleted ->
            releaseFrame.countDown()
            scheduler.start()
            scheduler.requestFrame()
            runBlocking { scheduler.stopAndJoin() }

            val completedAtStop = framesCompleted.get()
            repeat(5) { scheduler.requestFrame() }
            Thread.sleep(200)

            assertEquals(completedAtStop, framesCompleted.get(), "a frame ran after stopAndJoin")
        }

    /**
     * Runs [block] against a scheduler whose frame callback blocks until released, on a real
     * dispatcher — a virtual-time test scheduler cannot express "a frame is running on another
     * thread right now", which is the only thing this test is about.
     */
    private fun withScheduler(
        block: (
            scheduler: FrameScheduler,
            frameEntered: CountDownLatch,
            releaseFrame: CountDownLatch,
            framesCompleted: AtomicInteger,
        ) -> Unit,
    ) {
        val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
        val frameEntered = CountDownLatch(1)
        val releaseFrame = CountDownLatch(1)
        val framesCompleted = AtomicInteger(0)
        val scheduler =
            FrameScheduler(
                scope = scope,
                targetFps = 0,
                onFrame = {
                    frameEntered.countDown()
                    releaseFrame.await()
                    framesCompleted.incrementAndGet()
                },
            )
        try {
            block(scheduler, frameEntered, releaseFrame, framesCompleted)
        } finally {
            releaseFrame.countDown()
            scheduler.stop()
            scope.cancel()
        }
    }
}
