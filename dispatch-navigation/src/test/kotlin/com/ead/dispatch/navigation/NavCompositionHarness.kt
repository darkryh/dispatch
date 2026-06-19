package com.ead.dispatch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.withComposer
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Retained-composition harness for navigation tests.
 *
 * Backed by the real Compose runtime via [Composer]/[withComposer], so re-rendering
 * mutated state recomposes the SAME composition, and [close] disposes it (firing
 * RememberObserver.onForgotten / DisposableEffect.onDispose). This is what makes
 * the memory-leak regressions observable.
 */
internal class NavCompositionHarness(
    width: Int = 80,
    height: Int = 20,
) : AutoCloseable {
    private val terminal =
        Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = width,
            height = height,
            interactive = false,
        )
    private val composer = Composer()

    fun render(content: @Composable () -> Unit) {
        withComposer(composer) {
            composer.startComposition()
            CompositionLocalProvider(
                LocalTerminal provides terminal,
                LocalTerminalWidth provides terminal.size.width,
                LocalTerminalHeight provides terminal.size.height,
            ) {
                content()
            }
            composer.endComposition()
        }
        composer.getRootNode()?.measure(Constraints.fixedWidth(terminal.size.width))
    }

    override fun close() {
        composer.close()
    }
}
