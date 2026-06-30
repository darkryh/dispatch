package io.github.darkryh.dispatch.runtime

import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
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
        onFrameComposeAndRender: () -> Unit,
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

    private suspend fun shutdown(
        inputJob: Job,
        onBeforeShutdown: suspend () -> Unit,
    ) {
        try {
            onBeforeShutdown()
            inputJob.cancelAndJoin()
            frameScheduler.stop()
            renderer.handoffToShellPrompt()
        } finally {
            renderer.showCursor()
        }
    }
}
