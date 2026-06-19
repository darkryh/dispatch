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
    private val targetFps: Int,
    private val onFrame: () -> Unit,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val running = AtomicBoolean(false)
    private val frameRequests = Channel<Unit>(Channel.CONFLATED)
    private val lastFrameTime = AtomicLong(0L)
    private var job: Job? = null

    private val frameTimeMs: Long = if (targetFps > 0) 1000L / targetFps else 0L

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

    fun markFrame() {
        lastFrameTime.set(timeProvider())
    }

    private fun canRenderNow(): Boolean = running.get() && scope.isActive

    private suspend fun delayToRespectFrameBudget() {
        if (frameTimeMs <= 0) return
        val last = lastFrameTime.get()
        if (last <= 0) return

        val elapsed = timeProvider() - last
        val sleepTime = frameTimeMs - elapsed
        if (sleepTime > 0) {
            delay(sleepTime)
        }
    }

    private fun renderFrame() {
        onFrame()
        lastFrameTime.set(timeProvider())
    }

}
