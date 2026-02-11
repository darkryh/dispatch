package com.ead.dispatch.render

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A render loop that manages frame timing and dispatches renders.
 *
 * The render loop coalesces rapid state changes into single frames,
 * ensuring smooth animation while avoiding unnecessary renders.
 */
class RenderLoop(
    private val renderer: TerminalRenderer,
    /**
     * Target frame rate (frames per second).
     * Set to 0 for immediate rendering on state changes.
     */
    private val targetFps: Int = 30,
    /**
     * Coroutine scope for the render loop.
     */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    /**
     * Whether the render loop is running.
     */
    private val running = AtomicBoolean(false)

    /**
     * Render job.
     */
    private var renderJob: Job? = null

    /**
     * Last render timestamp.
     */
    private val lastRenderTime = AtomicLong(0)

    /**
     * Frame time in milliseconds.
     */
    private val frameTimeMs: Long
        get() = if (targetFps > 0) 1000L / targetFps else 0L

    /**
     * Current frame content provider.
     */
    private var contentProvider: (() -> List<String>)? = null

    /**
     * State flow for triggering re-renders.
     */
    private val renderTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /**
     * Start the render loop.
     *
     * @param provider Function that returns the current frame content.
     */
    fun start(provider: () -> List<String>) {
        if (!running.compareAndSet(false, true)) return

        contentProvider = provider

        renderJob =
            scope.launch {
                if (targetFps > 0) {
                    runFixedRateLoop()
                } else {
                    runTriggeredLoop()
                }
            }
    }

    private suspend fun runFixedRateLoop() {
        while (shouldContinueLoop()) {
            val startTime = System.currentTimeMillis()
            renderFrame()
            delayToRespectFrameBudget(startTime)
        }
    }

    private suspend fun runTriggeredLoop() {
        renderTrigger.collect {
            if (running.get()) {
                renderFrame()
            }
        }
    }

    private suspend fun shouldContinueLoop(): Boolean = currentCoroutineContext().isActive && running.get()

    private suspend fun delayToRespectFrameBudget(frameStartTime: Long) {
        val elapsed = System.currentTimeMillis() - frameStartTime
        val sleepTime = frameTimeMs - elapsed
        if (sleepTime > 0) {
            delay(sleepTime)
        }
    }

    /**
     * Stop the render loop.
     */
    fun stop() {
        running.set(false)
        renderJob?.cancel()
        renderJob = null
        contentProvider = null
    }

    /**
     * Request a render (for immediate mode).
     */
    fun requestRender() {
        if (targetFps == 0) {
            renderTrigger.tryEmit(Unit)
        }
        // In fixed frame rate mode, the next frame will pick up changes
    }

    /**
     * Force an immediate render (bypasses frame timing).
     */
    suspend fun renderNow(forceFullRedraw: Boolean = false) {
        val provider = contentProvider ?: return
        val lines = provider()
        renderer.render(lines, forceFullRedraw)
        lastRenderTime.set(System.currentTimeMillis())
    }

    /**
     * Render a single frame.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun renderFrame() {
        val provider = contentProvider ?: return

        try {
            val lines = provider()
            renderer.render(lines)
            lastRenderTime.set(System.currentTimeMillis())
        } catch (e: RuntimeException) {
            // Log error but continue rendering
            System.err.println("Render error: ${e.message}")
        }
    }

    /**
     * Check if the render loop is running.
     */
    fun isRunning(): Boolean = running.get()

    /**
     * Get time since last render in milliseconds.
     */
    fun timeSinceLastRender(): Long {
        val last = lastRenderTime.get()
        return if (last > 0) System.currentTimeMillis() - last else 0
    }
}

/**
 * A simplified render controller for simple use cases.
 */
class SimpleRenderer(
    terminal: Terminal,
) {
    private val renderer = TerminalRenderer(terminal)

    /**
     * Terminal width.
     */
    val width: Int get() = renderer.terminalWidth

    /**
     * Terminal height.
     */
    val height: Int get() = renderer.terminalHeight

    /**
     * Get current terminal width.
     */
    val terminalWidth: Int get() = renderer.terminalWidth

    /**
     * Get current terminal height.
     */
    val terminalHeight: Int get() = renderer.terminalHeight

    /**
     * Render lines to the screen.
     */
    fun render(lines: List<String>) {
        renderer.render(lines)
    }

    /**
     * Clear the screen.
     */
    fun clear() {
        renderer.clearScreen()
    }

    /**
     * Show the cursor.
     */
    fun showCursor() {
        renderer.showCursor()
    }

    /**
     * Hide the cursor.
     */
    fun hideCursor() {
        renderer.hideCursor()
    }

    /**
     * Move cursor to position.
     */
    fun moveCursor(
        x: Int,
        y: Int,
    ) {
        renderer.moveCursor(x, y)
    }
}
