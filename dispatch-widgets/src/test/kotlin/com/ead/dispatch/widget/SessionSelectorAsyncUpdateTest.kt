package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.CompositionLocalProvider
import com.ead.dispatch.runtime.EffectRunner
import com.ead.dispatch.runtime.KeyboardInterceptor
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.Recomposer
import com.ead.dispatch.runtime.withComposer
import com.ead.dispatch.viewmodel.collectAsState
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertTrue

class SessionSelectorAsyncUpdateTest {
    @Test
    fun `session selector updates when options flow emits`() = runBlocking {
        val terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 80,
            height = 20,
            interactive = false,
        )
        val keyboardInterceptor = KeyboardInterceptor()
        val composer = Composer()
        val recomposer = Recomposer(CoroutineScope(Dispatchers.Default + SupervisorJob()))
        val scopeToken = Any()

        val optionsFlow = MutableStateFlow<List<SessionOption<String>>>(emptyList())
        var lines: List<String> = emptyList()
        var renderCount = 0
        var hadRecomposer = false

        @Dispatchable
        fun Screen() {
            hadRecomposer = Recomposer.current != null
            val options = optionsFlow.collectAsState()
            SessionSelector(
                options = options.value,
                onOptionSelected = {},
                onExit = {},
                showFilter = false,
                showHeaders = true,
            )
        }

        fun compose() {
            withComposer(composer) {
                Recomposer.withRecomposer(recomposer) {
                    Recomposer.withScope(scopeToken) {
                        composer.startComposition()
                        CompositionLocalProvider(
                            LocalTerminal provides terminal,
                            LocalTerminalWidth provides terminal.size.width,
                            LocalTerminalHeight provides terminal.size.height,
                            LocalKeyboardInterceptor provides keyboardInterceptor,
                        ) {
                            Screen()
                        }
                        composer.endComposition()
                        EffectRunner.runPendingEffects()
                    }
                }
            }

            val root = composer.getRootNode()
            lines = root?.measure(Constraints.fixedWidth(80))?.lines ?: emptyList()
            renderCount += 1
        }

        compose()
        assertTrue(hadRecomposer, "recomposer not set inside Screen")
        withTimeout(1_000) { optionsFlow.subscriptionCount.first { it > 0 } }
        assertTrue(lines.any { it.contains("No matching sessions") })

        recomposer.registerComposition(scopeToken) { compose() }
        val job = recomposer.start()

        optionsFlow.value = listOf(
            SessionOption(
                id = "s1",
                title = "hi",
                updatedTime = "1 hours ago",
                conversationId = "s1",
                messageCount = 1,
                data = "s1",
            )
        )

        withTimeout(1_000) {
            val startCount = renderCount
            while (renderCount == startCount) {
                yield()
            }
        }

        assertTrue(lines.any { it.contains("hi") })

        recomposer.stop()
        job.cancel()
    }
}
