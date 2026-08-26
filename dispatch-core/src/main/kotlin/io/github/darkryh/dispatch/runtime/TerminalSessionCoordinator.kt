package io.github.darkryh.dispatch.runtime

import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.render.FrameOutput
import io.github.darkryh.dispatch.render.TerminalRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

internal class TerminalSessionCoordinator(
    private val terminal: Terminal,
    private val config: DispatchConfig,
    private val backgroundScope: CoroutineScope,
    private val frameScheduler: FrameScheduler,
    private val renderer: TerminalRenderer,
    private val resizeCoordinator: ResizeCoordinator,
) {
    suspend fun run(
        onInputLoop: suspend (suspend () -> Any?) -> Unit,
        onFrameComposeAndRender: suspend () -> Unit,
        awaitExit: suspend () -> Unit,
        onBeforeShutdown: suspend () -> Unit,
    ) {
        terminal.enterRawMode(config.mouseTracking).use { rawMode ->
            val initialSize = terminal.updateSize()
            resizeCoordinator.markInitialized(initialSize.width, initialSize.height)

            val inputJob =
                backgroundScope.launch(Dispatchers.IO) {
                    onInputLoop {
                        rawMode.readEventOrNull(50.milliseconds)
                    }
                }

            frameScheduler.start()
            onFrameComposeAndRender()
            frameScheduler.markFrame()

            try {
                // Suspend until an exit is requested (or this coroutine is cancelled when the UI
                // scope shuts down, which throws and runs the shutdown below) — no 50ms idle poll.
                awaitExit()
            } finally {
                withContext(NonCancellable) {
                    shutdown(
                        inputJob = inputJob,
                        onBeforeShutdown = onBeforeShutdown,
                    )
                }
            }
        }
    }

    /**
     * Order here is load-bearing, and used to be the wrong way round.
     *
     * The layout tree is single-threaded by design: every mutation (the recomposer's applier) and
     * every walk (measure, focus sync) happens on the UI dispatcher. Shutdown is the one path that
     * can break that, because it runs on the caller's thread — the one that owns `runBlocking` —
     * not on the UI dispatcher. Tearing the composition down FIRST therefore had
     * `Composition.dispose()` clearing `LayoutNode` children on the caller's thread while a frame
     * that the still-running scheduler had already started was iterating that exact list on the UI
     * thread. Sometimes it landed between frames and nothing happened; sometimes it landed inside
     * one and the frame coroutine died with a `ConcurrentModificationException`, which — with no
     * handler installed on the UI scope — reached the default uncaught-exception handler and
     * printed a stack trace over the terminal just as it was being handed back. That is the
     * intermittent "crash on /shutdown", and the busier the app, the wider the window.
     *
     * So: silence every producer before touching what they read.
     *  1. no further frame starts, and any frame already running has finished ([stopAndJoin]);
     *  2. no further input event can be dispatched;
     *  3. only then hand over to [onBeforeShutdown], which owns the composition teardown;
     *  4. and only once nothing can paint again, give the terminal back to the shell.
     */
    private suspend fun shutdown(
        inputJob: Job,
        onBeforeShutdown: suspend () -> Unit,
    ) {
        try {
            frameScheduler.stopAndJoin()
            inputJob.cancelAndJoin()
            onBeforeShutdown()
            renderer.handoffToShellPrompt()
        } finally {
            renderer.showCursor()
            // Hand stdout back, flushing anything still buffered. Matters for the embedded case,
            // where the host JVM keeps running after the TUI exits.
            FrameOutput.uninstall()
        }
    }
}
