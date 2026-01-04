package com.ead.dispatch.render

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
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
     * Channel for render requests.
     */
    private val renderRequests = Channel<RenderRequest>(Channel.CONFLATED)

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
    private val _renderTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /**
     * Start the render loop.
     *
     * @param provider Function that returns the current frame content.
     */
    fun start(provider: () -> List<String>) {
        if (!running.compareAndSet(false, true)) return

        contentProvider = provider

        renderJob = scope.launch {
            if (targetFps > 0) {
                // Fixed frame rate mode
                while (isActive && running.get()) {
                    val startTime = System.currentTimeMillis()

                    // Render the frame
                    renderFrame()

                    // Calculate sleep time to maintain frame rate
                    val elapsed = System.currentTimeMillis() - startTime
                    val sleepTime = frameTimeMs - elapsed
                    if (sleepTime > 0) {
                        delay(sleepTime)
                    }
                }
            } else {
                // Immediate mode - render on trigger
                _renderTrigger.collect {
                    if (running.get()) {
                        renderFrame()
                    }
                }
            }
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
            _renderTrigger.tryEmit(Unit)
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
    private fun renderFrame() {
        val provider = contentProvider ?: return

        try {
            val lines = provider()
            renderer.render(lines)
            lastRenderTime.set(System.currentTimeMillis())
        } catch (e: Exception) {
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
 * Internal render request.
 */
private data class RenderRequest(
    val forceFullRedraw: Boolean = false,
)

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
    fun moveCursor(x: Int, y: Int) {
        renderer.moveCursor(x, y)
    }
}
