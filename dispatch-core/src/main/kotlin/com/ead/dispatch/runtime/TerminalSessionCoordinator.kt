package com.ead.dispatch.runtime

import com.ead.dispatch.render.TerminalRenderer
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

internal class TerminalSessionCoordinator(
    private val terminal: Terminal,
    private val config: DispatchConfig,
    private val uiScope: CoroutineScope,
    private val backgroundScope: CoroutineScope,
    private val uiDispatcher: CoroutineDispatcher,
    private val frameScheduler: FrameScheduler,
    private val recomposer: Recomposer,
    private val compositionScopeToken: Any,
    private val renderer: TerminalRenderer,
    private val resizeCoordinator: ResizeCoordinator,
) {
    suspend fun run(
        onInputLoop: suspend (suspend () -> Any?) -> Unit,
        onFrameComposeAndRender: () -> Unit,
        shouldExit: () -> Boolean,
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
            recomposer.registerComposition(compositionScopeToken) { frameScheduler.requestFrame() }
            onFrameComposeAndRender()
            frameScheduler.markFrame()
            val recomposerJob = recomposer.start()

            try {
                awaitExitRequest(shouldExit)
            } finally {
                withContext(NonCancellable) {
                    shutdown(
                        inputJob = inputJob,
                        recomposerJob = recomposerJob,
                        onBeforeShutdown = onBeforeShutdown,
                    )
                }
            }
        }
    }

    private suspend fun awaitExitRequest(shouldExit: () -> Boolean) {
        while (!shouldExit() && uiScope.isActive) {
            delay(50)
        }
    }

    private suspend fun shutdown(
        inputJob: Job,
        recomposerJob: Job,
        onBeforeShutdown: suspend () -> Unit,
    ) {
        try {
            onBeforeShutdown()
            inputJob.cancelAndJoin()
            frameScheduler.stop()
            recomposer.stop()
            recomposerJob.cancelAndJoin()
            renderer.handoffToShellPrompt()
        } finally {
            renderer.showCursor()
        }
    }
}
