package com.ead.dispatch.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Coalesces render requests onto a fixed frame rate.
 */
internal class FrameScheduler(
    private val scope: CoroutineScope,
    targetFps: Int,
    private val onFrame: () -> Unit,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val running = AtomicBoolean(false)
    private val frameRequests = Channel<Unit>(Channel.CONFLATED)
    private val lastFrameTime = AtomicLong(0L)
    private var job: Job? = null

    @Volatile
    var targetFps: Int = targetFps
        private set

    @Volatile
    private var frameTimeMs: Long = fpsToFrameTimeMs(targetFps)

    private val bypassNextBudget = AtomicBoolean(false)

    /**
     * Change the paint cadence at runtime. Used by idle hibernation to throttle FPS down while idle
     * and restore it on wake. Takes effect on the next frame request.
     */
    fun setTargetFps(fps: Int) {
        targetFps = fps
        frameTimeMs = fpsToFrameTimeMs(fps)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return

        job =
            scope.launch {
                for (ignored in frameRequests) {
                    if (!canRenderNow()) return@launch
                    delayToRespectFrameBudget()
                    if (!canRenderNow()) return@launch
                    renderFrame()
                }
            }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        frameRequests.close()
        job?.cancel()
        job = null
    }

    fun requestFrame() {
        if (!running.get()) return
        frameRequests.trySend(Unit)
    }

    /**
     * Request a frame that skips the pacing budget for that one paint. Used to wake instantly from
     * hibernation so the restored FPS never delays the first frame after user input.
     */
    fun requestImmediateFrame() {
        bypassNextBudget.set(true)
        requestFrame()
    }

    fun markFrame() {
        lastFrameTime.set(timeProvider())
    }

    private fun canRenderNow(): Boolean = running.get() && scope.isActive

    private suspend fun delayToRespectFrameBudget() {
        if (bypassNextBudget.compareAndSet(true, false)) return
        val budget = frameTimeMs
        if (budget <= 0) return
        val last = lastFrameTime.get()
        if (last <= 0) return

        val elapsed = timeProvider() - last
        val sleepTime = budget - elapsed
        if (sleepTime > 0) {
            delay(sleepTime)
        }
    }

    private fun renderFrame() {
        onFrame()
        lastFrameTime.set(timeProvider())
    }

    private companion object {
        private fun fpsToFrameTimeMs(fps: Int): Long = if (fps > 0) 1000L / fps else 0L
    }
}
