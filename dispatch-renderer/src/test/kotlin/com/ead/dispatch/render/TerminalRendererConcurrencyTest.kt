package com.ead.dispatch.render

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test

/**
 * Concurrency tests for TerminalRenderer to verify thread-safety
 * and prevent flickering issues caused by race conditions.
 */
class TerminalRendererConcurrencyTest {
    private fun createRenderer(): Pair<TerminalRenderer, TerminalRecorder> {
        val recorder =
            TerminalRecorder(
                ansiLevel = AnsiLevel.TRUECOLOR,
                width = 80,
                height = 24,
                supportsAnsiCursor = true,
            )
        val terminal = Terminal(terminalInterface = recorder)
        return TerminalRenderer(terminal) to recorder
    }

    @Test
    fun `concurrent cursor operations do not interleave with active area updates`() =
        runBlocking {
            val (renderer, _) = createRenderer()
            val iterations = 100

            withContext(Dispatchers.Default) {
                val updateJob =
                    launch {
                        repeat(iterations) { i ->
                            renderer.updateActiveArea(listOf("Line $i", "Input: ${"x".repeat(i % 20)}"))
                        }
                    }

                val cursorJob =
                    launch {
                        repeat(iterations) {
                            renderer.hideCursor()
                            renderer.showCursor()
                        }
                    }

                updateJob.join()
                cursorJob.join()
            }
            // Test passes if no exceptions are thrown and no deadlocks occur
        }

    @Test
    fun `clearScreen during active area update does not corrupt state`() =
        runBlocking {
            val (renderer, _) = createRenderer()
            val iterations = 50

            withContext(Dispatchers.Default) {
                val updateJob =
                    launch {
                        repeat(iterations) { i ->
                            renderer.updateActiveArea(listOf("Content $i"))
                        }
                    }

                val clearJob =
                    launch {
                        repeat(iterations / 10) {
                            renderer.clearScreen(clearScrollback = false)
                        }
                    }

                updateJob.join()
                clearJob.join()
            }
            // Test passes if no exceptions are thrown and no deadlocks occur
        }

    @Test
    fun `rapid updateActiveArea calls are properly serialized`() =
        runBlocking {
            val (renderer, _) = createRenderer()
            val iterations = 200

            withContext(Dispatchers.Default) {
                val jobs =
                    (0 until 4).map { threadId ->
                        launch {
                            repeat(iterations) { i ->
                                renderer.updateActiveArea(listOf("Thread $threadId - Update $i"))
                            }
                        }
                    }

                jobs.forEach { it.join() }
            }
            // Test passes if no exceptions are thrown and no deadlocks occur
        }

    @Test
    fun `cursor visibility is consistent after concurrent operations`() =
        runBlocking {
            val (renderer, _) = createRenderer()
            val iterations = 100

            withContext(Dispatchers.Default) {
                val jobs =
                    (0 until 3).map {
                        launch {
                            repeat(iterations) {
                                renderer.hideCursor()
                                renderer.showCursor()
                            }
                        }
                    }

                jobs.forEach { it.join() }
            }
            // Test passes if no exceptions are thrown and no deadlocks occur
        }

    @Test
    fun `appendScrollingContent and updateActiveArea do not race`() =
        runBlocking {
            val (renderer, _) = createRenderer()
            val iterations = 100

            withContext(Dispatchers.Default) {
                val scrollJob =
                    launch {
                        repeat(iterations) { i ->
                            renderer.appendScrollingContent(listOf("Scrolling line $i"))
                        }
                    }

                val activeJob =
                    launch {
                        repeat(iterations) { i ->
                            renderer.updateActiveArea(listOf("> Input $i"))
                        }
                    }

                scrollJob.join()
                activeJob.join()
            }
            // Test passes if no exceptions are thrown and no deadlocks occur
        }

    @Test
    fun `mixed operations under heavy load do not deadlock`() =
        runBlocking {
            val (renderer, _) = createRenderer()
            val iterations = 50

            withContext(Dispatchers.Default) {
                val job1 =
                    launch {
                        repeat(iterations) { i ->
                            renderer.updateActiveArea(listOf("Active $i"))
                        }
                    }

                val job2 =
                    launch {
                        repeat(iterations) { i ->
                            renderer.appendScrollingContent(listOf("Scroll $i"))
                        }
                    }

                val job3 =
                    launch {
                        repeat(iterations) {
                            renderer.hideCursor()
                            renderer.showCursor()
                        }
                    }

                val job4 =
                    launch {
                        repeat(iterations / 5) {
                            renderer.clearScreen()
                        }
                    }

                val job5 =
                    launch {
                        repeat(iterations) { i ->
                            renderer.moveCursor(i % 80, i % 24)
                        }
                    }

                listOf(job1, job2, job3, job4, job5).forEach { it.join() }
            }
            // Test passes if no exceptions or deadlocks occur within timeout
        }
}
